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
import java.util.function.Supplier;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
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


	DefaultWebGraphQlTester(RequestStrategy requestStrategy, @Nullable HttpHeaders defaultHeaders) {
		super(requestStrategy);
		this.defaultHeaders = defaultHeaders;
	}


	@Override
	public WebRequestSpec query(String query) {
		return new DefaultWebRequestSpec(getRequestStrategy(), query, this.defaultHeaders);
	}


	/**
	 * Default implementation to build {@link WebGraphQlTester}.
	 */
	final static class DefaultBuilder implements WebGraphQlTester.Builder {

		private final Supplier<RequestStrategy> requestStrategySupplier;

		private final GraphQlTesterBuilderConfig builderConfig = new GraphQlTesterBuilderConfig();

		@Nullable
		private HttpHeaders headers;

		DefaultBuilder(WebTestClient client) {
			this.requestStrategySupplier = () -> {
				Duration timeout = this.builderConfig.getResponseTimeout();
				WebTestClient clientToUse = client.mutate().responseTimeout(timeout).build();
				return new WebTestClientRequestStrategy(clientToUse, this.builderConfig);
			};
		}

		DefaultBuilder(WebGraphQlHandler handler) {
			this.requestStrategySupplier = () -> new WebGraphQlHandlerRequestStrategy(handler, this.builderConfig);
		}

		@Override
		public WebGraphQlTester.Builder errorFilter(Predicate<GraphQLError> predicate) {
			this.builderConfig.errorFilter(predicate);
			return this;
		}

		@Override
		public DefaultBuilder jsonPathConfig(Configuration config) {
			this.builderConfig.jsonPathConfig(config);
			return this;
		}

		@Override
		public DefaultBuilder responseTimeout(Duration timeout) {
			this.builderConfig.responseTimeout(timeout);
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
	 * {@link RequestStrategy} that works as an HTTP client with requests executed through
	 * {@link WebTestClient} that in turn may work connect with or without a live server
	 * for Spring MVC and WebFlux.
	 */
	private static class WebTestClientRequestStrategy implements RequestStrategy {

		private final WebTestClient client;

		private final GraphQlTesterBuilderConfig builderConfig;

		WebTestClientRequestStrategy(WebTestClient client, GraphQlTesterBuilderConfig builderConfig) {
			this.client = client;
			this.builderConfig = builderConfig;
		}

		@Nullable
		private Predicate<GraphQLError> errorFilter() {
			return this.builderConfig.getErrorFilter();
		}

		private Configuration jsonPathConfig() {
			return this.builderConfig.getJsonPathConfig();
		}

		@Override
		public ResponseSpec execute(RequestInput requestInput) {
			EntityExchangeResult<byte[]> result = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> headers.putAll(getHeaders(requestInput)))
					.bodyValue(requestInput)
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
			DocumentContext documentContext = JsonPath.parse(content, jsonPathConfig());

			return new DefaultResponseSpec(documentContext, errorFilter(), result::assertWithDiagnostics);
		}

		@Override
		public SubscriptionSpec executeSubscription(RequestInput requestInput) {
			FluxExchangeResult<TestExecutionResult> exchangeResult = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> headers.putAll(getHeaders(requestInput)))
					.bodyValue(requestInput)
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.returnResult(TestExecutionResult.class);

			return new DefaultSubscriptionSpec(
					exchangeResult.getResponseBody().cast(ExecutionResult.class),
					errorFilter(), jsonPathConfig(), exchangeResult::assertWithDiagnostics);
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
	private static class WebGraphQlHandlerRequestStrategy extends AbstractDirectRequestStrategy {

		private final WebGraphQlHandler graphQlHandler;

		WebGraphQlHandlerRequestStrategy(WebGraphQlHandler handler, GraphQlTesterBuilderConfig builderConfig) {
			super(builderConfig);
			this.graphQlHandler = handler;
		}

		protected ExecutionResult executeInternal(RequestInput input) {
			Assert.isInstanceOf(WebInput.class, input);
			WebInput webInput = (WebInput) input;
			ExecutionResult result = this.graphQlHandler.handle(webInput).block(responseTimeout());
			Assert.notNull(result, "Expected ExecutionResult");
			return result;
		}

	}

	private static final class DefaultWebRequestSpec implements WebRequestSpec {

		private static final URI DEFAULT_URL = URI.create("");

		private final RequestSpecDelegate delegate;

		private final HttpHeaders headers = new HttpHeaders();

		DefaultWebRequestSpec(RequestStrategy requestStrategy, String query, @Nullable HttpHeaders headers) {
			this.delegate = new RequestSpecDelegate(requestStrategy, query);
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
			this.delegate.operationName(name);
			return this;
		}

		@Override
		public WebRequestSpec variable(String name, Object value) {
			this.delegate.variable(name, value);
			return this;
		}

		@Override
		public ResponseSpec execute() {
			return this.delegate.execute(createRequestInput());
		}

		@Override
		public void executeAndVerify() {
			this.delegate.executeAndVerify(createRequestInput());
		}

		@Override
		public SubscriptionSpec executeSubscription() {
			return this.delegate.executeSubscription(createRequestInput());
		}

		private RequestInput createRequestInput() {
			RequestInput requestInput = this.delegate.createRequestInput();
			return new WebInput(DEFAULT_URL, this.headers, requestInput.toMap(), null);
		}

	}

}
