/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collections;

import graphql.ExecutionResultImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.MockExecutionGraphQlService;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.webflux.GraphQlHttpHandler;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.lang.Nullable;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.reactive.server.HttpHandlerConnector;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Tests for the {@link HttpSyncGraphQlClient} builder performing requests to a
 * {@link GraphQlHttpHandler} with a {@link MockExecutionGraphQlService} that
 * always returns an empty GraphQL response. The main goal however is to capture
 * the WebInput on the server side through a {@link WebGraphQlInterceptor}.
 *
 * <p>The equivalent of {@link WebGraphQlClientBuilderTests} but for
 * {@link HttpSyncGraphQlClient}.
 *
 * @author Rossen Stoyanchev
 */
public class HttpSyncGraphQlClientBuilderTests {

	private static final String DOCUMENT = "{ Query }";

	private final ClientBuilderSetup setup = new ClientBuilderSetup();


	@Test
	void mutateUrlHeaders() {

		String url = "/graphql-one";

		// Original
		HttpSyncGraphQlClient.Builder<?> builder = this.setup.initBuilder()
				.url(url)
				.headers(headers -> headers.add("h", "one"));

		HttpSyncGraphQlClient client = builder.build();
		client.document(DOCUMENT).executeSync();

		WebGraphQlRequest request = this.setup.getActualRequest();
		assertThat(request.getUri().toString()).isEqualTo(url);
		assertThat(request.getHeaders().get("h")).containsExactly("one");

		// Mutate to add header value
		builder = client.mutate().headers(headers -> headers.add("h", "two"));
		client = builder.build();
		client.document(DOCUMENT).executeSync();
		assertThat(setup.getActualRequest().getHeaders().get("h")).containsExactly("one", "two");

		// Mutate to replace header
		builder = client.mutate().header("h", "three", "four");
		client = builder.build();
		client.document(DOCUMENT).executeSync();

		request = this.setup.getActualRequest();
		assertThat(request.getUri().toString()).isEqualTo(url);
		assertThat(request.getHeaders().get("h")).containsExactly("three", "four");
	}

	@Test
	void mutateWebTestClientViaConsumer() {

		// Original header value
		HttpSyncGraphQlClient.Builder<?> builder = this.setup.initBuilder()
				.restClient(testClientBuilder -> testClientBuilder.defaultHeaders(h -> h.add("h", "one")));

		HttpSyncGraphQlClient client = builder.build();
		client.document(DOCUMENT).executeSync();
		assertThat(setup.getActualRequest().getHeaders().get("h")).containsExactly("one");

		// Mutate to add header value
		HttpSyncGraphQlClient.Builder<?> builder2 = client.mutate()
				.restClient(testClientBuilder -> testClientBuilder.defaultHeaders(h -> h.add("h", "two")));

		client = builder2.build();
		client.document(DOCUMENT).executeSync();
		assertThat(setup.getActualRequest().getHeaders().get("h")).containsExactly("one", "two");

		// Mutate to replace header
		HttpSyncGraphQlClient.Builder<?> builder3 = client.mutate()
				.restClient(testClientBuilder -> testClientBuilder.defaultHeader("h", "three"));

		client = builder3.build();
		client.document(DOCUMENT).executeSync();
		assertThat(setup.getActualRequest().getHeaders().get("h")).containsExactly("three");
	}

	@Test
	void mutateDocumentSource() {

		DocumentSource documentSource = name -> name.equals("name") ?
				Mono.just(DOCUMENT) : Mono.error(new IllegalArgumentException());

		// Original
		HttpSyncGraphQlClient.Builder<?> builder = this.setup.initBuilder().documentSource(documentSource);
		HttpSyncGraphQlClient client = builder.build();
		client.documentName("name").executeSync();

		WebGraphQlRequest request = this.setup.getActualRequest();
		assertThat(request.getDocument()).isEqualTo(DOCUMENT);

		// Mutate
		client = client.mutate().build();
		client.documentName("name").executeSync();

		request = this.setup.getActualRequest();
		assertThat(request.getDocument()).isEqualTo(DOCUMENT);
	}

	@Test
	void urlEncoding() {

		HttpSyncGraphQlClient client = this.setup.initBuilder().url("/graphql one").build();
		client.document(DOCUMENT).executeSync();

		assertThat(this.setup.getActualRequest().getUri().toString()).isEqualTo("/graphql%20one");
	}

	@Test
	void contentTypeDefault() {

		this.setup.initBuilder().build().document(DOCUMENT).executeSync();

		WebGraphQlRequest request = this.setup.getActualRequest();
		assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	void contentTypeOverride() {
		MediaType testMediaType = new MediaType("application", "graphql-request+json");

		setup.initBuilder()
				.header(HttpHeaders.CONTENT_TYPE, "application/graphql-request+json").build()
				.document(DOCUMENT).executeSync();

		WebGraphQlRequest request = this.setup.getActualRequest();
		assertThat(request.getHeaders().getContentType()).isEqualTo(testMediaType);

	}

	@Test
	void codecConfigurerRegistersJsonPathMappingProvider() {

		TestJackson2JsonConverter testConverter = new TestJackson2JsonConverter();

		HttpSyncGraphQlClient.Builder<?> builder = this.setup.initBuilder();
		builder.messageConverters(converters -> converters.add(0, testConverter));

		String document = "{me {name}}";
		MovieCharacter character = MovieCharacter.create("Luke Skywalker");
		this.setup.getGraphQlService().setResponse(document,
				ExecutionResultImpl.newExecutionResult()
						.data(Collections.singletonMap("me", character))
						.build());

		HttpSyncGraphQlClient client = builder.build();
		ClientGraphQlResponse response = client.document(document).executeSync();

		testConverter.resetLastValue();

		assertThat(response).isNotNull();
		assertThat(response.field("me").toEntity(MovieCharacter.class).getName()).isEqualTo("Luke Skywalker");

		Object lastValue = testConverter.getLastValue();
		assertThat(lastValue).isEqualTo(character);
	}


	private static class ClientBuilderSetup {

		private final MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();

		@Nullable
		private WebGraphQlRequest graphQlRequest;

		public ClientBuilderSetup() {
			this.graphQlService.setDefaultResponse("{}");
		}

		public MockExecutionGraphQlService getGraphQlService() {
			return this.graphQlService;
		}

		public WebGraphQlRequest getActualRequest() {
			Assert.state(this.graphQlRequest != null, "No saved WebGraphQlRequest");
			return this.graphQlRequest;
		}

		public HttpSyncGraphQlClient.Builder<?> initBuilder() {
			HttpHandler httpHandler = initServer();
			ClientHttpRequestFactory requestFactory = new HttpHandlerClientHttpRequestFactory(httpHandler);
			return HttpSyncGraphQlClient.builder(RestClient.builder().requestFactory(requestFactory));
		}

		private HttpHandler initServer() {
			GraphQlHttpHandler handler = new GraphQlHttpHandler(WebGraphQlHandler.builder(this.graphQlService)
					.interceptor((request, chain) -> {
						this.graphQlRequest = request;
						return chain.next(graphQlRequest);
					})
					.build());
			RouterFunction<ServerResponse> routerFunction = route().POST("/**", handler::handleRequest).build();
			return RouterFunctions.toHttpHandler(routerFunction, HandlerStrategies.withDefaults());
		}

	}


	private static final class HttpHandlerClientHttpRequestFactory implements ClientHttpRequestFactory {

		private final WebClient webClient;

		private HttpHandlerClientHttpRequestFactory(HttpHandler httpHandler) {
			ClientHttpConnector connector = new HttpHandlerConnector(httpHandler);
			this.webClient = WebClient.builder().clientConnector(connector).build();
		}

			@Override
			public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
				return new MockClientHttpRequest(httpMethod, uri) {
					@Override
					protected ClientHttpResponse executeInternal() {
						return getClientHttpResponse(httpMethod, uri, getHeaders(), getBodyAsBytes());
					}
				};
			}

			private ClientHttpResponse getClientHttpResponse(
					HttpMethod httpMethod, URI uri, HttpHeaders requestHeaders, byte[] requestBody) {

				ResponseEntity<byte[]> entity = this.webClient.method(httpMethod).uri(uri)
						.headers(headers -> headers.putAll(requestHeaders))
						.bodyValue(requestBody)
						.retrieve()
						.toEntity(byte[].class)
						.block();

				byte[] body = (entity.getBody() != null ? entity.getBody() : new byte[0]);
				MockClientHttpResponse response = new MockClientHttpResponse(body, entity.getStatusCode());
				response.getHeaders().putAll(entity.getHeaders());
				return response;
			}
	}


	private static class TestJackson2JsonConverter extends MappingJackson2HttpMessageConverter {

		@Nullable
		private Object lastValue;

		@Nullable
		Object getLastValue() {
			return this.lastValue;
		}

		@Override
		public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
				throws IOException, HttpMessageNotReadableException {

			this.lastValue = super.read(type, contextClass, inputMessage);
			return this.lastValue;
		}

		void resetLastValue() {
			this.lastValue = null;
		}

	}

}
