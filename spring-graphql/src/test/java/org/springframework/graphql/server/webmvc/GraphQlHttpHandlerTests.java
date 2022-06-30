/*
 * Copyright 2002-2022 the original author or authors.
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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.Test;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.coercing.webmvc.UploadCoercing;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.graphql.client.MultipartBodyCreator.createFilePartsAndMapping;

/**
 * Tests for {@link GraphQlHttpHandler}.
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
public class GraphQlHttpHandlerTests {

	private static final List<HttpMessageConverter<?>> MESSAGE_READERS =
			Collections.singletonList(new MappingJackson2HttpMessageConverter());

	private final GraphQlHttpHandler greetingHandler = GraphQlSetup.schemaContent("type Query { greeting: String }")
			.queryFetcher("greeting", (env) -> "Hello").toHttpHandler();

    private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldProduceApplicationJsonByDefault() throws Exception {
		MockHttpServletRequest servletRequest = createServletRequest("{\"query\":\"{ greeting }\"}", "*/*");
		MockHttpServletResponse servletResponse = handleRequest(servletRequest, this.greetingHandler);
		assertThat(servletResponse.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
	}

	@Test
	void shouldProduceApplicationGraphQl() throws Exception {
		MockHttpServletRequest servletRequest = createServletRequest("{\"query\":\"{ greeting }\"}", MediaType.APPLICATION_GRAPHQL_VALUE);
		MockHttpServletResponse servletResponse = handleRequest(servletRequest, this.greetingHandler);
		assertThat(servletResponse.getContentType()).isEqualTo(MediaType.APPLICATION_GRAPHQL_VALUE);
	}

	@Test
	void shouldProduceApplicationJson() throws Exception {
		MockHttpServletRequest servletRequest = createServletRequest("{\"query\":\"{ greeting }\"}", "application/json");
		MockHttpServletResponse servletResponse = handleRequest(servletRequest, this.greetingHandler);
		assertThat(servletResponse.getContentType()).isEqualTo("application/json");
	}

	@Test
	void locale() throws Exception {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "Hello in " + env.getLocale())
				.toHttpHandler();
		MockHttpServletRequest servletRequest = createServletRequest("{\"query\":\"{ greeting }\"}", MediaType.APPLICATION_GRAPHQL_VALUE);
		LocaleContextHolder.setLocale(Locale.FRENCH);

		try {
			MockHttpServletResponse servletResponse = handleRequest(servletRequest, handler);

			assertThat(servletResponse.getContentAsString())
					.isEqualTo("{\"data\":{\"greeting\":\"Hello in fr\"}}");
		}
		finally {
			LocaleContextHolder.resetLocaleContext();
		}
	}

    @Test
    void shouldPassFile() throws Exception {
        GraphQlHttpHandler handler = GraphQlSetup.schemaContent(
                        "type Query { ping: String } \n" +
                        "scalar Upload\n" +
                        "type Mutation {\n" +
                        "    fileUpload(fileInput: Upload!): String!\n" +
                        "}")
                .mutationFetcher("fileUpload", (env) -> ((MultipartFile) env.getVariables().get("fileInput")).getOriginalFilename())
                .runtimeWiring(builder -> builder.scalar(GraphQLScalarType.newScalar()
                        .name("Upload")
                        .coercing(new UploadCoercing())
                        .build()))
                .toHttpHandler();
        MockHttpServletRequest servletRequest = createMultipartServletRequest(
                "mutation FileUpload($fileInput: Upload!) " +
                "{fileUpload(fileInput: $fileInput) }",
                MediaType.APPLICATION_GRAPHQL_VALUE,
                Collections.singletonMap("fileInput", new ClassPathResource("/foo.txt"))
        );

        MockHttpServletResponse servletResponse = handleMultipartRequest(servletRequest, handler);

        assertThat(servletResponse.getContentAsString())
                .isEqualTo("{\"data\":{\"fileUpload\":\"foo.txt\"}}");
    }

    @Test
    void shouldPassListOfFiles() throws Exception {
        GraphQlHttpHandler handler = GraphQlSetup.schemaContent(
                        "type Query { ping: String } \n" +
                                "scalar Upload\n" +
                                "type Mutation {\n" +
                                "    multipleFilesUpload(multipleFileInputs: [Upload!]!): [String!]!\n" +
                                "}")
                .mutationFetcher("multipleFilesUpload", (env) -> ((Collection<MultipartFile>) env.getVariables().get("multipleFileInputs")).stream().map(multipartFile -> multipartFile.getOriginalFilename()).collect(Collectors.toList()))
                .runtimeWiring(builder -> builder.scalar(GraphQLScalarType.newScalar()
                        .name("Upload")
                        .coercing(new UploadCoercing())
                        .build()))
                .toHttpHandler();

        Collection<Resource> resources = new ArrayList<>();
        resources.add(new ClassPathResource("/foo.txt"));
        resources.add(new ClassPathResource("/bar.txt"));

        MockHttpServletRequest servletRequest = createMultipartServletRequest(
                "mutation MultipleFilesUpload($multipleFileInputs: [Upload!]!) " +
                        "{multipleFilesUpload(multipleFileInputs: $multipleFileInputs) }",
                MediaType.APPLICATION_GRAPHQL_VALUE,
                Collections.singletonMap("multipleFileInputs", resources)
        );

        MockHttpServletResponse servletResponse = handleMultipartRequest(servletRequest, handler);

        assertThat(servletResponse.getContentAsString())
                .isEqualTo("{\"data\":{\"multipleFilesUpload\":[\"foo.txt\",\"bar.txt\"]}}");
    }

	@Test
	void shouldSetExecutionId() throws Exception {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { showId: ID! }")
				.queryFetcher("showId", (env) -> env.getExecutionId().toString())
				.toHttpHandler();

		MockHttpServletRequest servletRequest = createServletRequest("{\"query\":\"{ showId }\"}", MediaType.APPLICATION_GRAPHQL_VALUE);

		MockHttpServletResponse servletResponse = handleRequest(servletRequest, handler);
		DocumentContext document = JsonPath.parse(servletResponse.getContentAsString());
		String id = document.read("data.showId", String.class);
		assertThatNoException().isThrownBy(() -> UUID.fromString(id));
	}

	private MockHttpServletRequest createServletRequest(String query, String accept) {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
		servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
		servletRequest.setContent(query.getBytes(StandardCharsets.UTF_8));
		servletRequest.addHeader("Accept", accept);
		servletRequest.setAsyncSupported(true);
		return servletRequest;
	}

    private MockHttpServletRequest createMultipartServletRequest(String query, String accept, Map<String, Object> files) {
        MockMultipartHttpServletRequest servletRequest = new MockMultipartHttpServletRequest();
        servletRequest.addHeader("Accept", accept);
        servletRequest.setAsyncSupported(true);

        Map<String, List<String>> partMappings = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        operations.put("query", query);
        Map<String, Object> variables = new HashMap<>();
        createFilePartsAndMapping(files, variables, partMappings,
                (partName, objectResource) -> servletRequest.addFile(getMultipartFile(partName, objectResource))
        );
        operations.put("variables", variables);

        servletRequest.addPart(new MockPart("operations", getJsonArray(operations)));
        servletRequest.addPart(new MockPart("map", getJsonArray(partMappings)));

        return servletRequest;
    }

    private MockMultipartFile getMultipartFile(String partName, Object objectResource) {
        Resource resource = (Resource) objectResource;
        try {
            return new MockMultipartFile(partName, resource.getFilename(), null, resource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getFileByteArray(Resource resource) {
        try {
            byte[] targetArray = new byte[(int)resource.getFile().length()];
            try(InputStream inputStream = resource.getInputStream()) {
                inputStream.read(targetArray);
                return targetArray;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getJsonArray(Object o) {
        try {
            return objectMapper.writeValueAsBytes(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private MockHttpServletResponse handleRequest(
			MockHttpServletRequest servletRequest, GraphQlHttpHandler handler) throws ServletException, IOException {

		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = ((AsyncServerResponse) handler.handleRequest(request)).block();

		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		response.writeTo(servletRequest, servletResponse, new DefaultContext());
		return servletResponse;
	}

    private MockHttpServletResponse handleMultipartRequest(
            MockHttpServletRequest servletRequest, GraphQlHttpHandler handler) throws ServletException, IOException {

        ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
        ServerResponse response = ((AsyncServerResponse) handler.handleMultipartRequest(request)).block();

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        response.writeTo(servletRequest, servletResponse, new DefaultContext());
        return servletResponse;
    }

	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageConverter<?>> messageConverters() {
			return MESSAGE_READERS;
		}

	}

}
