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
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.GraphQLError;
import reactor.core.publisher.Flux;

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
class DefaultWebGraphQlTester implements WebGraphQlTester {

	private final WebRequestStrategy requestStrategy;

	@Nullable
	private final HttpHeaders defaultHeaders;


	DefaultWebGraphQlTester(WebRequestStrategy requestStrategy, @Nullable HttpHeaders defaultHeaders) {
		Assert.notNull(requestStrategy, "WebRequestStrategy is required.");
		this.requestStrategy = requestStrategy;
		this.defaultHeaders = defaultHeaders;
	}


	@Override
	public WebRequestSpec query(String query) {
		return new DefaultWebRequestSpec(this.requestStrategy, this.defaultHeaders, query);
	}


	/**
	 * Default implementation to build {@link WebGraphQlTester}.
	 */
	final static class DefaultBuilder
			extends DefaultGraphQlTester.BuilderSupport implements WebGraphQlTester.Builder {

		@Nullable
		private final WebTestClient client;

		@Nullable
		private final WebGraphQlHandler handler;

		@Nullable
		private HttpHeaders headers;

		DefaultBuilder(WebTestClient client) {
			Assert.notNull(client, "WebTestClient is required.");
			this.client = client;
			this.handler = null;
		}

		DefaultBuilder(WebGraphQlHandler handler) {
			Assert.notNull(handler, "WebGraphQlHandler is required.");
			this.handler = handler;
			this.client = null;
		}

		@Override
		public WebGraphQlTester.Builder errorFilter(Predicate<GraphQLError> predicate) {
			addErrorFilter(predicate);
			return this;
		}

		@Override
		public DefaultBuilder jsonPathConfig(Configuration config) {
			setJsonPathConfig(config);
			return this;
		}

		@Override
		public DefaultBuilder responseTimeout(Duration timeout) {
			setResponseTimeout(timeout);
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
			WebRequestStrategy requestStrategy;
			if (this.client != null) {
				WebTestClient clientToUse = this.client;
				if (getResponseTimeout() != null) {
					clientToUse = this.client.mutate().responseTimeout(getResponseTimeout()).build();
				}
				requestStrategy = new WebTestClientRequestStrategy(
						clientToUse, getErrorFilter(), initJsonPathConfig(), getResponseTimeout());
			}
			else if (this.handler != null) {
				requestStrategy = new WebGraphQlHandlerRequestStrategy(
						this.handler, getErrorFilter(), initJsonPathConfig(), initResponseTimeout());
			}
			else {
				throw new IllegalStateException("Neither client nor handler");
			}
			return new DefaultWebGraphQlTester(requestStrategy, this.headers);
		}
	}


	/**
	 * Extension of {@code RequestStrategy} for performing a GraphQL request
	 * in a web environment.
	 */
	interface WebRequestStrategy {

		/**
		 * Perform a request with the given {@link WebInput} container.
		 * @param input the request input
		 * @return the response spec
		 */
		WebResponseSpec execute(WebInput input);

		/**
		 * Perform a subscription with the given {@link WebInput} container.
		 * @param input the request input
		 * @return the subscription spec
		 */
		WebSubscriptionSpec executeSubscription(WebInput input);

	}



	/**
	 * {@link WebRequestStrategy} that works as an HTTP client with requests executed through
	 * {@link WebTestClient} that in turn may work connect with or without a live server
	 * for Spring MVC and WebFlux.
	 */
	private static class WebTestClientRequestStrategy
			extends DefaultGraphQlTester.RequestStrategySupport implements WebRequestStrategy {

		private final WebTestClient client;

		public WebTestClientRequestStrategy(WebTestClient client,
				@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration responseTimeout) {

			super(errorFilter, jsonPathConfig, responseTimeout);
			this.client = client;
		}

		@Override
		public WebResponseSpec execute(WebInput webInput) {
			EntityExchangeResult<byte[]> result = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> headers.putAll(webInput.getHeaders()))
					.bodyValue(webInput.toMap())
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

			DocumentContext documentContext = JsonPath.parse(content, getJsonPathConfig());
			ResponseSpec responseSpec = createResponseSpec(documentContext, result::assertWithDiagnostics);
			return new DefaultWebResponseSpec(responseSpec, result.getResponseHeaders());
		}

		@Override
		public WebSubscriptionSpec executeSubscription(WebInput webInput) {
			FluxExchangeResult<TestExecutionResult> exchangeResult = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> headers.putAll(webInput.getHeaders()))
					.bodyValue(webInput.toMap())
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.returnResult(TestExecutionResult.class);

			Flux<ResponseSpec> flux = exchangeResult.getResponseBody()
					.map((result) -> createResponseSpec(result, exchangeResult::assertWithDiagnostics));

			return new DefaultWebSubscriptionSpec(() -> flux, exchangeResult.getResponseHeaders());
		}
	}


	/**
	 * {@link WebRequestStrategy} that performs requests directly on
	 * {@link WebGraphQlHandler}, i.e. Web request testing without a transport.
	 */
	private static class WebGraphQlHandlerRequestStrategy
			extends DefaultGraphQlTester.DirectRequestStrategySupport implements WebRequestStrategy {

		private final WebGraphQlHandler graphQlHandler;

		WebGraphQlHandlerRequestStrategy(WebGraphQlHandler handler,
				@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration timeout) {

			super(errorFilter, jsonPathConfig, timeout);
			this.graphQlHandler = handler;
		}

		@Override
		public WebResponseSpec execute(WebInput input) {
			WebOutput webOutput = executeInternal(input);
			ResponseSpec responseSpec = createResponseSpec(input, webOutput);
			return new DefaultWebResponseSpec(responseSpec, webOutput.getResponseHeaders());
		}

		@Override
		public WebSubscriptionSpec executeSubscription(WebInput input) {
			WebOutput webOutput = executeInternal(input);
			SubscriptionSpec subscriptionSpec = createSubscriptionSpec(input, webOutput);
			return new DefaultWebSubscriptionSpec(subscriptionSpec, webOutput.getResponseHeaders());
		}

		private WebOutput executeInternal(WebInput webInput) {
			WebOutput webOutput = this.graphQlHandler.handle(webInput).block(getResponseTimeout());
			Assert.notNull(webOutput, "Expected WebOutput");
			return webOutput;
		}
	}


	private static final class DefaultWebRequestSpec
			extends DefaultGraphQlTester.RequestSpecSupport implements WebRequestSpec {

		private static final URI DEFAULT_URL = URI.create("");

		private final WebRequestStrategy requestStrategy;

		private final HttpHeaders headers = new HttpHeaders();

		DefaultWebRequestSpec(
				WebRequestStrategy requestStrategy, @Nullable HttpHeaders defaultHeaders, String query) {

			super(query);
			Assert.notNull(requestStrategy, "WebRequestStrategy is required");
			this.requestStrategy = requestStrategy;
			if (!CollectionUtils.isEmpty(defaultHeaders)) {
				this.headers.putAll(defaultHeaders);
			}
		}

		@Override
		public WebRequestSpec operationName(@Nullable String name) {
			setOperationName(name);
			return this;
		}

		@Override
		public WebRequestSpec variable(String name, Object value) {
			addVariable(name, value);
			return this;
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
		public WebResponseSpec execute() {
			return this.requestStrategy.execute(createWebInput());
		}

		@Override
		public void executeAndVerify() {
			verify(execute());
		}

		@Override
		public WebSubscriptionSpec executeSubscription() {
			return this.requestStrategy.executeSubscription(createWebInput());
		}

		private WebInput createWebInput() {
			return new WebInput(DEFAULT_URL, this.headers, createRequestInput().toMap(), null);
		}
	}


	private static final class DefaultWebResponseSpec implements WebResponseSpec {

		private final ResponseSpec responseSpec;

		private final HttpHeaders headers;

		public DefaultWebResponseSpec(ResponseSpec responseSpec, @Nullable HttpHeaders headers) {
			this.responseSpec = responseSpec;
			this.headers = (headers != null ? headers : new HttpHeaders());
		}

		@Override
		public ResponseSpec httpHeadersSatisfy(Consumer<HttpHeaders> consumer) {
			consumer.accept(this.headers);
			return this;
		}

		@Override
		public PathSpec path(String path) {
			return this.responseSpec.path(path);
		}

		@Override
		public ErrorSpec errors() {
			return this.responseSpec.errors();
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
