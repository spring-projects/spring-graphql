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
package org.springframework.graphql.server.webflux;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.coercing.webflux.UploadCoercing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.http.MediaType;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.graphql.client.MultipartBodyCreator.createFilePartsAndMapping;

/**
 * Tests for {@link GraphQlHttpHandler}.
 * @author Rossen Stoyanchev
 */
public class GraphQlHttpHandlerTests {

	private final GraphQlHttpHandler greetingHandler = GraphQlSetup.schemaContent("type Query { greeting: String }")
			.queryFetcher("greeting", (env) -> "Hello").toHttpHandlerWebFlux();

    private final Jackson2JsonEncoder jackson2JsonEncoder = new Jackson2JsonEncoder();

    @Test
	void shouldProduceApplicationJsonByDefault() {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.ALL).build();

		MockServerHttpResponse httpResponse = handleRequest(
				httpRequest, this.greetingHandler, Collections.singletonMap("query", "{greeting}"));

		assertThat(httpResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

    @Test
    void shouldPassFile() {
        GraphQlHttpHandler handler = GraphQlSetup.schemaContent(
                        "type Query { ping: String } \n" +
                        "scalar Upload\n" +
                        "type Mutation {\n" +
                        "    fileUpload(fileInput: Upload!): String!\n" +
                        "}")
                .mutationFetcher("fileUpload", (env) -> ((FilePart) env.getVariables().get("fileInput")).filename())
                .runtimeWiring(builder -> builder.scalar(GraphQLScalarType.newScalar()
                        .name("Upload")
                        .coercing(new UploadCoercing())
                        .build()))
                .toHttpHandlerWebFlux();

        MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
                .contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.ALL)
                .build();

        MockServerHttpResponse httpResponse = handleMultipartRequest(
                httpRequest, handler, "mutation FileUpload($fileInput: Upload!) " +
                        "{fileUpload(fileInput: $fileInput) }",
                Collections.emptyMap(),
                Collections.singletonMap("fileInput", new ClassPathResource("/foo.txt"))
        );

        assertThat(httpResponse.getBodyAsString().block())
                .isEqualTo("{\"data\":{\"fileUpload\":\"foo.txt\"}}");
    }

    @Test
    void shouldPassListOfFiles() {
        GraphQlHttpHandler handler = GraphQlSetup.schemaContent(
                        "type Query { ping: String } \n" +
                                "scalar Upload\n" +
                                "type Mutation {\n" +
                                "    multipleFilesUpload(multipleFileInputs: [Upload!]!): [String!]!\n" +
                                "}")
                .mutationFetcher("multipleFilesUpload", (env) -> ((Collection<FilePart>) env.getVariables().get("multipleFileInputs")).stream().map(FilePart::filename).collect(Collectors.toList()))
                .runtimeWiring(builder -> builder.scalar(GraphQLScalarType.newScalar()
                        .name("Upload")
                        .coercing(new UploadCoercing())
                        .build()))
                .toHttpHandlerWebFlux();

        MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
                .contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.ALL)
                .build();

        Collection<Resource> resources = new ArrayList<>();
        resources.add(new ClassPathResource("/foo.txt"));
        resources.add(new ClassPathResource("/bar.txt"));
        MockServerHttpResponse httpResponse = handleMultipartRequest(
                httpRequest, handler, "mutation MultipleFilesUpload($multipleFileInputs: [Upload!]!) " +
                        "{multipleFilesUpload(multipleFileInputs: $multipleFileInputs) }",
                Collections.emptyMap(),
                Collections.singletonMap("multipleFileInputs", resources)
        );

        assertThat(httpResponse.getBodyAsString().block())
                .isEqualTo("{\"data\":{\"multipleFilesUpload\":[\"foo.txt\",\"bar.txt\"]}}");
    }

	@Test
	void shouldProduceApplicationGraphQl() {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_GRAPHQL).accept(MediaType.APPLICATION_GRAPHQL).build();

		MockServerHttpResponse httpResponse = handleRequest(
				httpRequest, this.greetingHandler, Collections.singletonMap("query", "{greeting}"));

		assertThat(httpResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_GRAPHQL);
	}

	@Test
	void shouldProduceApplicationJson() {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).build();

		MockServerHttpResponse httpResponse = handleRequest(
				httpRequest, this.greetingHandler, Collections.singletonMap("query", "{greeting}"));

		assertThat(httpResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	void locale() {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "Hello in " + env.getLocale())
				.toHttpHandlerWebFlux();

		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_GRAPHQL).accept(MediaType.APPLICATION_GRAPHQL).acceptLanguageAsLocales(Locale.FRENCH).build();

		MockServerHttpResponse httpResponse = handleRequest(
				httpRequest, handler, Collections.singletonMap("query", "{greeting}"));

		assertThat(httpResponse.getBodyAsString().block())
				.isEqualTo("{\"data\":{\"greeting\":\"Hello in fr\"}}");
	}

	@Test
	void shouldSetExecutionId() {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { showId: String }")
				.queryFetcher("showId", (env) -> env.getExecutionId().toString())
				.toHttpHandlerWebFlux();

		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_GRAPHQL).accept(MediaType.APPLICATION_GRAPHQL).build();

		MockServerHttpResponse httpResponse = handleRequest(
				httpRequest, handler, Collections.singletonMap("query", "{showId}"));

		DocumentContext document = JsonPath.parse(httpResponse.getBodyAsString().block());
		String id = document.read("data.showId", String.class);
		assertThat(id).isEqualTo(httpRequest.getId());
	}

	private MockServerHttpResponse handleRequest(
			MockServerHttpRequest httpRequest, GraphQlHttpHandler handler, Map<String, String> body) {

		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);

		MockServerRequest serverRequest = MockServerRequest.builder()
				.exchange(exchange)
				.uri(((ServerWebExchange) exchange).getRequest().getURI())
				.method(((ServerWebExchange) exchange).getRequest().getMethod())
				.headers(((ServerWebExchange) exchange).getRequest().getHeaders())
				.body(Mono.just((Object) body));

		handler.handleRequest(serverRequest)
				.flatMap(response -> response.writeTo(exchange, new DefaultContext()))
				.block();

		return exchange.getResponse();
	}

    private MockServerHttpResponse handleMultipartRequest(
            MockServerHttpRequest httpRequest, GraphQlHttpHandler handler, String body,
            Map<String, Object> requestVariables, Map<String, Object> files) {

        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);

        LinkedMultiValueMap<String, Part> parts = new LinkedMultiValueMap<>();

        Map<String, List<String>> partMappings = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        operations.put("query", body);
        Map<String, Object> variables = new HashMap<>(requestVariables);
        createFilePartsAndMapping(files, variables, partMappings, (partName, resource) -> addFilePart(parts, partName, (Resource) resource));
        operations.put("variables", variables);
        addJsonEncodedPart(parts, "operations", operations);

        addJsonEncodedPart(parts, "map", partMappings);

        MockServerRequest serverRequest = MockServerRequest.builder()
                .exchange(exchange)
                .uri(((ServerWebExchange) exchange).getRequest().getURI())
                .method(((ServerWebExchange) exchange).getRequest().getMethod())
                .headers(((ServerWebExchange) exchange).getRequest().getHeaders())
                .body(Mono.just(parts));

        handler.handleMultipartRequest(serverRequest)
                .flatMap(response -> response.writeTo(exchange, new DefaultContext()))
                .block();

        return exchange.getResponse();
    }

    private void addJsonEncodedPart(LinkedMultiValueMap<String, Part> parts, String name, Object toSerialize) {
        ResolvableType resolvableType = ResolvableType.forClass(HashMap.class);
        Flux<DataBuffer> bufferFlux = jackson2JsonEncoder.encode(
                Mono.just(toSerialize),
                DefaultDataBufferFactory.sharedInstance,
                resolvableType,
                MediaType.APPLICATION_JSON,
                null
        );
        TestPart part = new TestPart(name, bufferFlux);
        parts.add(name, part);
    }

    private void addFilePart(LinkedMultiValueMap<String, Part> parts, String name, Resource resource) {
        Flux<DataBuffer> dataBufferFlux = DataBufferUtils.read(resource, DefaultDataBufferFactory.sharedInstance, 1024);
        TestFilePart filePart = new TestFilePart(name, resource.getFilename(), dataBufferFlux);
        parts.add(name, filePart);
    }

	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return Collections.singletonList(new EncoderHttpMessageWriter<>(new Jackson2JsonEncoder()));
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return Collections.emptyList();
		}

	}

    private static class TestPart implements Part {

        private final String name;


        private final Flux<DataBuffer> content;

        private TestPart(String name,  Flux<DataBuffer> content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public HttpHeaders headers() {
            return new HttpHeaders();
        }

        @Override
        public Flux<DataBuffer> content() {
            return content;
        }
    }

    private static class TestFilePart implements FilePart {

        private final String name;

        private final String filename;

        private final Flux<DataBuffer> content;

        private TestFilePart(String name, String filename, Flux<DataBuffer> content) {
            this.name = name;
            this.filename = filename;
            this.content = content;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public HttpHeaders headers() {
            return new HttpHeaders();
        }

        @Override
        public Flux<DataBuffer> content() {
            return content;
        }

        @Override
        public String filename() {
            return filename;
        }

        @Override
        public Mono<Void> transferTo(Path dest) {
            return Mono.error(new RuntimeException("Not implemented"));
        }
    }

}
