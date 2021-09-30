/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.graphql.test.tester;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import reactor.core.publisher.Flux;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Default implementation of {@link WebGraphQlTester}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultWebGraphQlTester extends DefaultGraphQlTester implements WebGraphQlTester {

	@Nullable
	private final HttpHeaders defaultHeaders;


	DefaultWebGraphQlTester(WebRequestStrategy requestStrategy, @Nullable HttpHeaders defaultHeaders) {
		super(requestStrategy);
		this.defaultHeaders = defaultHeaders;
	}


	@Override
	public WebRequestSpec query(String query) {
		return new DefaultWebRequestSpec((WebRequestStrategy) getRequestStrategy(), query, this.defaultHeaders);
	}


	/**
	 * Default implementation to build {@link WebGraphQlTester}.
	 */
	final static class DefaultBuilder implements WebGraphQlTester.Builder {

		@Nullable
		private Predicate<GraphQLError> errorFilter;

		@Nullable
		private Configuration jsonPathConfig;

		@Nullable
		private Duration responseTimeout;

		private final Supplier<WebRequestStrategy> requestStrategySupplier;

		@Nullable
		private HttpHeaders headers;

		DefaultBuilder(WebTestClient client) {
			this.requestStrategySupplier = () -> {
				WebTestClient clientToUse = (this.responseTimeout != null ?
						client.mutate().responseTimeout(this.responseTimeout).build() : client);
				return new WebTestClientRequestStrategy(clientToUse, this.errorFilter, initJsonPathConfig());
			};
		}

		DefaultBuilder(WebGraphQlHandler handler) {
			this.requestStrategySupplier = () ->
					new WebGraphQlHandlerRequestStrategy(
							handler, this.errorFilter, initJsonPathConfig(),
							(this.responseTimeout != null ? this.responseTimeout : Duration.ofSeconds(5)));
		}

		private Configuration initJsonPathConfig() {
			return JsonPathConfiguration.initialize(this.jsonPathConfig);
		}

		@Override
		public WebGraphQlTester.Builder errorFilter(Predicate<GraphQLError> predicate) {
			this.errorFilter = (this.errorFilter != null ? errorFilter.and(predicate) : predicate);
			return this;
		}

		@Override
		public DefaultBuilder jsonPathConfig(Configuration config) {
			this.jsonPathConfig = config;
			return this;
		}

		@Override
		public DefaultBuilder responseTimeout(Duration timeout) {
			Assert.notNull(timeout, "'timeout' is required");
			this.responseTimeout = timeout;
			return this;
		}

		@Override
		public DefaultBuilder defaultHeader(String headerName, String... headerValues) {
			this.headers = (this.headers != null ? this.headers : new HttpHeaders());
			for (String headerValue : headerValues) {
				this.headers.add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public WebGraphQlTester.Builder defaultHeaders(Consumer<HttpHeaders> headersConsumer) {
			this.headers = (this.headers != null ? this.headers : new HttpHeaders());
			headersConsumer.accept(this.headers);
			return this;
		}

		@Override
		public WebGraphQlTester build() {
			return new DefaultWebGraphQlTester(this.requestStrategySupplier.get(), this.headers);
		}
	}


	/**
	 * Extension of {@code RequestStrategy} for performing a GraphQL request
	 * in a web environment.
	 */
	interface WebRequestStrategy extends RequestStrategy {

		/**
		 * Perform a request with the given {@link RequestInput} container.
		 * @param input the request input
		 * @return the response spec
		 */
		WebResponseSpec execute(RequestInput input);

		/**
		 * Perform a subscription with the given {@link RequestInput} container.
		 * @param input the request input
		 * @return the subscription spec
		 */
		WebSubscriptionSpec executeSubscription(RequestInput input);

	}



	/**
	 * {@link RequestStrategy} that works as an HTTP client with requests executed through
	 * {@link WebTestClient} that in turn may work connect with or without a live server
	 * for Spring MVC and WebFlux.
	 */
	private static class WebTestClientRequestStrategy implements WebRequestStrategy {

		private final WebTestClient client;

		@Nullable
		private final Predicate<GraphQLError> errorFilter;

		private final Configuration jsonPathConfig;

		public WebTestClientRequestStrategy(
				WebTestClient client, @Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig) {

			this.client = client;
			this.errorFilter = errorFilter;
			this.jsonPathConfig = jsonPathConfig;
		}

		@Override
		public WebResponseSpec execute(RequestInput requestInput) {
			EntityExchangeResult<byte[]> result = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> headers.putAll(getHeaders(requestInput)))
					.bodyValue(requestInput.toMap())
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentType(MediaType.APPLICATION_JSON)
					.expectBody()
					.returnResult();

			byte[] bytes = result.getResponseBodyContent();
			Assert.notNull(bytes, "Expected GraphQL response content");
			String content = new String(bytes, StandardCharsets.UTF_8);
			DocumentContext documentContext = JsonPath.parse(content, this.jsonPathConfig);

			ResponseSpec responseSpec =
					new DefaultResponseSpec(documentContext, this.errorFilter, result::assertWithDiagnostics);

			return new DefaultWebResponseSpec(responseSpec, result.getResponseHeaders());
		}

		@Override
		public WebSubscriptionSpec executeSubscription(RequestInput requestInput) {
			FluxExchangeResult<TestExecutionResult> exchangeResult = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> headers.putAll(getHeaders(requestInput)))
					.bodyValue(requestInput.toMap())
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.returnResult(TestExecutionResult.class);

			SubscriptionSpec subscriptionSpec = new DefaultSubscriptionSpec(
					exchangeResult.getResponseBody().cast(ExecutionResult.class),
					this.errorFilter, this.jsonPathConfig, exchangeResult::assertWithDiagnostics);

			return new DefaultWebSubscriptionSpec(subscriptionSpec, exchangeResult.getResponseHeaders());
		}

		private HttpHeaders getHeaders(RequestInput requestInput) {
			Assert.isInstanceOf(WebInput.class, requestInput);
			return ((WebInput) requestInput).getHeaders();
		}

	}

	/**
	 * {@link RequestStrategy} that performs requests directly on
	 * {@link WebGraphQlHandler}, i.e. Web request testing without a transport.
	 */
	private static class WebGraphQlHandlerRequestStrategy extends DirectRequestStrategySupport implements WebRequestStrategy {

		private final WebGraphQlHandler graphQlHandler;

		WebGraphQlHandlerRequestStrategy(WebGraphQlHandler handler,
				@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration timeout) {

			super(errorFilter, jsonPathConfig, timeout);
			this.graphQlHandler = handler;
		}

		@Override
		public WebResponseSpec execute(RequestInput input) {
			WebOutput webOutput = executeInternal(input);
			ResponseSpec responseSpec = createResponseSpec(input, webOutput);
			return new DefaultWebResponseSpec(responseSpec, webOutput.getResponseHeaders());
		}

		@Override
		public WebSubscriptionSpec executeSubscription(RequestInput input) {
			WebOutput webOutput = executeInternal(input);
			SubscriptionSpec spec = createSubscriptionSpec(input, webOutput);
			return new DefaultWebSubscriptionSpec(spec, webOutput.getResponseHeaders());
		}

		private WebOutput executeInternal(RequestInput input) {
			Assert.isInstanceOf(WebInput.class, input);
			WebInput webInput = (WebInput) input;
			WebOutput webOutput = this.graphQlHandler.handle(webInput).block(responseTimeout());
			Assert.notNull(webOutput, "Expected WebOutput");
			return webOutput;
		}
	}

	private static final class DefaultWebRequestSpec implements WebRequestSpec {

		private static final URI DEFAULT_URL = URI.create("");

		private final WebRequestStrategy requestStrategy;

		private final String query;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private final HttpHeaders headers = new HttpHeaders();

		DefaultWebRequestSpec(WebRequestStrategy requestStrategy, String query, @Nullable HttpHeaders headers) {
			Assert.notNull(requestStrategy, "WebRequestStrategy is required");
			Assert.notNull(query, "`query` is required");
			this.requestStrategy = requestStrategy;
			this.query = query;
			if (!CollectionUtils.isEmpty(headers)) {
				this.headers.putAll(headers);
			}
		}

		@Override
		public WebRequestSpec header(String headerName, String... headerValues) {
			for (String headerValue : headerValues) {
				this.headers.add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public WebRequestSpec headers(Consumer<HttpHeaders> headersConsumer) {
			headersConsumer.accept(this.headers);
			return this;
		}

		@Override
		public WebRequestSpec operationName(@Nullable String name) {
			this.operationName = name;
			return this;
		}

		@Override
		public WebRequestSpec variable(String name, Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public WebResponseSpec execute() {
			return this.requestStrategy.execute(createRequestInput());
		}

		@Override
		public void executeAndVerify() {
			execute().path("$.errors").valueIsEmpty();
		}

		@Override
		public WebSubscriptionSpec executeSubscription() {
			return this.requestStrategy.executeSubscription(createRequestInput());
		}

		private RequestInput createRequestInput() {
			Map<String, Object> body = new LinkedHashMap<>(3);
			body.put("query", this.query);
			if (this.operationName != null) {
				body.put("operationName", this.operationName);
			}
			if (!CollectionUtils.isEmpty(this.variables)) {
				body.put("variables", new LinkedHashMap<>(this.variables));
			}
			return new WebInput(DEFAULT_URL, this.headers, body, null);
		}
	}


	private static final class DefaultWebResponseSpec implements WebResponseSpec {

		private final ResponseSpec delegate;

		private final HttpHeaders headers;

		public DefaultWebResponseSpec(ResponseSpec delegate, @Nullable HttpHeaders headers) {
			this.delegate = delegate;
			this.headers = (headers != null ? headers : new HttpHeaders());
		}

		@Override
		public ResponseSpec httpHeadersSatisfy(Consumer<HttpHeaders> consumer) {
			consumer.accept(this.headers);
			return this;
		}

		@Override
		public PathSpec path(String path) {
			return this.delegate.path(path);
		}

		@Override
		public ErrorSpec errors() {
			return this.delegate.errors();
		}
	}


	private static final class DefaultWebSubscriptionSpec implements WebSubscriptionSpec {

		private final SubscriptionSpec delegate;

		private final HttpHeaders headers;

		public DefaultWebSubscriptionSpec(SubscriptionSpec delegate, @Nullable HttpHeaders headers) {
			this.delegate = delegate;
			this.headers = (headers != null ? headers : new HttpHeaders());
		}

		@Override
		public SubscriptionSpec httpHeadersSatisfy(Consumer<HttpHeaders> consumer) {
			consumer.accept(this.headers);
			return this;
		}

		@Override
		public <T> Flux<T> toFlux(String path, Class<T> entityType) {
			return this.delegate.toFlux(path, entityType);
		}

		@Override
		public Flux<ResponseSpec> toFlux() {
			return this.delegate.toFlux();
		}
	}

}
