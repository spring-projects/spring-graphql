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

package org.springframework.graphql.server.webflux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.graphql.server.support.MultipartVariableMapper;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.util.function.Tuple2;

/**
 * WebFlux.fn Handler for GraphQL over HTTP requests.
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

	private final WebGraphQlHandler graphQlHandler;

    private final Decoder<?> jsonDecoder;

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
        this.jsonDecoder = new Jackson2JsonDecoder();
	}

    public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, Decoder<?> jsonDecoder) {
        Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
        Assert.notNull(jsonDecoder, "Decoder is required");
        this.graphQlHandler = graphQlHandler;
        this.jsonDecoder = jsonDecoder;
    }

	/**
	 * Handle GraphQL requests over HTTP.
	 * @param serverRequest the incoming HTTP request
	 * @return the HTTP response
	 */
	public Mono<ServerResponse> handleRequest(ServerRequest serverRequest) {
		return serverRequest.bodyToMono(MAP_PARAMETERIZED_TYPE_REF)
				.flatMap(body -> {
					WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
							serverRequest.uri(), serverRequest.headers().asHttpHeaders(), body,
							serverRequest.exchange().getRequest().getId(),
							serverRequest.exchange().getLocaleContext().getLocale());
					if (logger.isDebugEnabled()) {
						logger.debug("Executing: " + graphQlRequest);
					}
					return this.graphQlHandler.handleRequest(graphQlRequest);
				})
				.flatMap(response -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Execution complete");
					}
					ServerResponse.BodyBuilder builder = ServerResponse.ok();
					builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
					builder.contentType(selectResponseMediaType(serverRequest));
					return builder.bodyValue(response.toMap());
				});
	}

    @SuppressWarnings("unchecked")
	public Mono<ServerResponse> handleMultipartRequest(ServerRequest serverRequest) {
		return serverRequest.multipartData()
			.flatMap(multipartMultiMap -> {
				Map<String, Part> allParts = multipartMultiMap.toSingleValueMap();

				Optional<Part> operation = Optional.ofNullable(allParts.get("operations"));
				Optional<Part> mapParam = Optional.ofNullable(allParts.get("map"));

                Decoder<Map<String, Object>> mapJsonDecoder = (Decoder<Map<String, Object>>) jsonDecoder;
                Decoder<Map<String, List<String>>> listJsonDecoder = (Decoder<Map<String, List<String>>>) jsonDecoder;

                Mono<Map<String, Object>> inputQueryMono = operation
                    .map(part -> mapJsonDecoder.decodeToMono(
                            part.content(), ResolvableType.forType(MAP_PARAMETERIZED_TYPE_REF),
                            MediaType.APPLICATION_JSON, null
                    )).orElse(Mono.just(new HashMap<>()));

                Mono<Map<String, List<String>>> fileMapInputMono = mapParam
                    .map(part -> listJsonDecoder.decodeToMono(part.content(),
                            ResolvableType.forType(LIST_PARAMETERIZED_TYPE_REF),
                            MediaType.APPLICATION_JSON, null
                    )).orElse(Mono.just(new HashMap<>()));

                return Mono.zip(inputQueryMono, fileMapInputMono)
                    .flatMap((Tuple2<Map<String, Object>, Map<String, List<String>>> objects) -> {
                        Map<String, Object> inputQuery = objects.getT1();
                        Map<String, List<String>> fileMapInput = objects.getT2();

                        final Map<String, Object> queryVariables = getFromMapOrEmpty(inputQuery, "variables");
                        final Map<String, Object> extensions = getFromMapOrEmpty(inputQuery, "extensions");

                        fileMapInput.forEach((String fileKey, List<String> objectPaths) -> {
                            Part part = allParts.get(fileKey);
                            if (part != null) {
                                Assert.isInstanceOf(FilePart.class, part, "Part should be of type FilePart");
                                FilePart file = (FilePart) part;
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

                        Map<String, Object> body = Map.of(
                                "query", query, "operationName", StringUtils.hasText(opName) ? opName : "", "variables", queryVariables, "extensions", extensions);

                        WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
                                serverRequest.uri(), serverRequest.headers().asHttpHeaders(),
                                body,
                                serverRequest.exchange().getRequest().getId(),
                                serverRequest.exchange().getLocaleContext().getLocale());

                        if (logger.isDebugEnabled()) {
                            logger.debug("Executing: " + graphQlRequest);
                        }
                        return this.graphQlHandler.handleRequest(graphQlRequest);
                    });
            })
			.flatMap(response -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Execution complete");
				}
				ServerResponse.BodyBuilder builder = ServerResponse.ok();
				builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
				builder.contentType(selectResponseMediaType(serverRequest));
				return builder.bodyValue(response.toMap());
			});
	}

    @SuppressWarnings("unchecked")
	private Map<String, Object> getFromMapOrEmpty(Map<String, Object> input, String key) {
		if (input.containsKey(key)) {
			return (Map<String, Object>)input.get(key);
		} else {
			return new HashMap<>();
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
