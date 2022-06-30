/*
 * Copyright 2020-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.server.webmvc;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.graphql.server.support.MultipartVariableMapper;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import reactor.core.publisher.Mono;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.IdGenerator;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler to expose as a WebMvc.fn endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Nikita Konev
 * @since 1.0.0
 */
public class GraphQlHttpHandler {

	private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {};

    private static final ParameterizedTypeReference<Map<String, List<String>>> LIST_PARAMETERIZED_TYPE_REF =
            new ParameterizedTypeReference<Map<String, List<String>>>() {};

	private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
			Arrays.asList(MediaType.APPLICATION_GRAPHQL, MediaType.APPLICATION_JSON);

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

	private final WebGraphQlHandler graphQlHandler;

    private final PartReader partReader;

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
        this.partReader = new JacksonPartReader(new ObjectMapper());
	}

    public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, PartReader partReader) {
        Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
        Assert.notNull(partReader, "PartConverter is required");
        this.graphQlHandler = graphQlHandler;
        this.partReader = partReader;
    }

	/**
	 * Handle GraphQL requests over HTTP.
	 * @param serverRequest the incoming HTTP request
	 * @return the HTTP response
	 * @throws ServletException may be raised when reading the request body, e.g.
	 * {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handleRequest(ServerRequest serverRequest) throws ServletException {

		WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
				serverRequest.uri(), serverRequest.headers().asHttpHeaders(), readBody(serverRequest),
				this.idGenerator.generateId().toString(), LocaleContextHolder.getLocale());

		if (logger.isDebugEnabled()) {
			logger.debug("Executing: " + graphQlRequest);
		}

		Mono<ServerResponse> responseMono = this.graphQlHandler.handleRequest(graphQlRequest)
				.map(response -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Execution complete");
					}
					ServerResponse.BodyBuilder builder = ServerResponse.ok();
					builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
					builder.contentType(selectResponseMediaType(serverRequest));
					return builder.body(response.toMap());
				});

		return ServerResponse.async(responseMono);
	}

	public ServerResponse handleMultipartRequest(ServerRequest serverRequest) throws ServletException {
        HttpServletRequest httpServletRequest = serverRequest.servletRequest();

        Map<String, Object> inputQuery = Optional.ofNullable(this.<Map<String, Object>>deserializePart(
            httpServletRequest,
            "operations",
            MAP_PARAMETERIZED_TYPE_REF.getType()
        )).orElse(new HashMap<>());

		final Map<String, Object> queryVariables = getFromMapOrEmpty(inputQuery, "variables");
		final Map<String, Object> extensions = getFromMapOrEmpty(inputQuery, "extensions");

        Map<String, MultipartFile> fileParams = readMultipartFiles(httpServletRequest);

        Map<String, List<String>> fileMappings = Optional.ofNullable(this.<Map<String, List<String>>>deserializePart(
                httpServletRequest,
                "map",
                LIST_PARAMETERIZED_TYPE_REF.getType()
        )).orElse(new HashMap<>());

        fileMappings.forEach((String fileKey, List<String> objectPaths) -> {
			MultipartFile file = fileParams.get(fileKey);
			if (file != null) {
				objectPaths.forEach((String objectPath) -> {
					MultipartVariableMapper.mapVariable(
						objectPath,
						queryVariables,
						file
					);
				});
			}
		});

		String query = (String) inputQuery.get("query");
		String opName = (String) inputQuery.get("operationName");

		WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
			serverRequest.uri(), serverRequest.headers().asHttpHeaders(),
			query,
			opName,
			queryVariables,
			extensions,
			this.idGenerator.generateId().toString(), LocaleContextHolder.getLocale());

		if (logger.isDebugEnabled()) {
			logger.debug("Executing: " + graphQlRequest);
		}

		Mono<ServerResponse> responseMono = this.graphQlHandler.handleRequest(graphQlRequest)
			.map(response -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Execution complete");
				}
				ServerResponse.BodyBuilder builder = ServerResponse.ok();
				builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
				builder.contentType(selectResponseMediaType(serverRequest));
				return builder.body(response.toMap());
			});

		return ServerResponse.async(responseMono);
	}

    private <T> T deserializePart(HttpServletRequest httpServletRequest, String name, Type type) {
        try {
            Part part = httpServletRequest.getPart(name);
            if (part == null) {
                return null;
            }
            return partReader.readPart(part, type);
        } catch (IOException | ServletException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFromMapOrEmpty(Map<String, Object> input, String key) {
		if (input.containsKey(key)) {
			return (Map<String, Object>)input.get(key);
		} else {
			return new HashMap<>();
		}
	}

    private static Map<String, MultipartFile> readMultipartFiles(HttpServletRequest httpServletRequest) {
        Assert.isInstanceOf(MultipartHttpServletRequest.class, httpServletRequest,
                "Request should be of type MultipartHttpServletRequest");
        MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) httpServletRequest;
        return multipartHttpServletRequest.getFileMap();
    }

	private static Map<String, Object> readBody(ServerRequest request) throws ServletException {
		try {
			return request.body(MAP_PARAMETERIZED_TYPE_REF);
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}
	}

	private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
		for (MediaType accepted : serverRequest.headers().accept()) {
			if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
				return accepted;
			}
		}
		return MediaType.APPLICATION_JSON;
	}

}
