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
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.web.WebInput;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/**
 * Default implementation of {@link WebGraphQlTester}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultWebGraphQlTester implements WebGraphQlTester {

	private final WebRequestStrategy requestStrategy;

	@Nullable
	private final HttpHeaders defaultHeaders;

	private final Function<String, String> operationNameResolver;


	DefaultWebGraphQlTester(
			WebRequestStrategy requestStrategy, @Nullable HttpHeaders defaultHeaders,
			Function<String, String> operationNameResolver) {

		Assert.notNull(requestStrategy, "WebRequestStrategy is required.");
		this.requestStrategy = requestStrategy;
		this.defaultHeaders = defaultHeaders;
		this.operationNameResolver = operationNameResolver;
	}


	@Override
	public WebRequestSpec query(String query) {
		return new DefaultWebRequestSpec(this.requestStrategy, this.defaultHeaders, query);
	}

	@Override
	public WebRequestSpec operationName(String operationName) {
		return query(this.operationNameResolver.apply(operationName));
	}


	/**
	 * Factory for {@link WebGraphQlTester.ResponseSpec}, for use from
	 * {@link WebRequestStrategy} implementations.
	 */
	static WebResponseSpec createResponseSpec(
			ResponseSpec responseSpec, @Nullable HttpHeaders responseHeaders) {

		return new DefaultWebResponseSpec(responseSpec, responseHeaders);
	}

	/**
	 * Factory for {@link WebGraphQlTester.SubscriptionSpec}, for use from
	 * {@link WebRequestStrategy} implementations.
	 */
	static WebSubscriptionSpec createSubscriptionSpec(
			SubscriptionSpec subscriptionSpec, @Nullable HttpHeaders responseHeaders) {

		return new DefaultWebSubscriptionSpec(subscriptionSpec, responseHeaders);
	}


	/**
	 * {@link WebRequestSpec} that also collects HTTP request headers, in
	 * addition to the query, operationName, and variables.
	 */
	private static final class DefaultWebRequestSpec
			extends GraphQlTesterRequestSpecSupport implements WebRequestSpec {

		private static final URI DEFAULT_URL = URI.create("");

		private final WebRequestStrategy requestStrategy;

		private final HttpHeaders headers = new HttpHeaders();

		private DefaultWebRequestSpec(
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
		public WebRequestSpec variable(String name, @Nullable Object value) {
			addVariable(name, value);
			return this;
		}

		@Override
		public WebRequestSpec locale(Locale locale) {
			setLocale(locale);
			return this;
		}

		@Override
		public WebRequestSpec httpHeader(String headerName, String... headerValues) {
			for (String headerValue : headerValues) {
				this.headers.add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public WebRequestSpec httpHeaders(Consumer<HttpHeaders> headersConsumer) {
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
			RequestInput input = createRequestInput();
			return new WebInput(DEFAULT_URL, this.headers, input.toMap(), input.getLocale(),
					(input.getId() != null) ? input.getId() : ObjectUtils.getIdentityHexString(input));
		}
	}


	/**
	 * {@link WebResponseSpec} that exposes response headers and delegates
	 * all other methods to the given {@link GraphQlTester.ResponseSpec}.
	 */
	private static final class DefaultWebResponseSpec implements WebResponseSpec {

		private final ResponseSpec responseSpec;

		private final HttpHeaders responseHeaders;

		public DefaultWebResponseSpec(ResponseSpec responseSpec, @Nullable HttpHeaders responseHeaders) {
			this.responseSpec = responseSpec;
			this.responseHeaders = (responseHeaders != null ? responseHeaders : new HttpHeaders());
		}

		@Override
		public ResponseSpec httpHeadersSatisfy(Consumer<HttpHeaders> consumer) {
			consumer.accept(this.responseHeaders);
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


	/**
	 * {@link WebSubscriptionSpec} that exposes response headers and delegates
	 * all other methods to the given {@link GraphQlTester.SubscriptionSpec}.
	 */
	private static final class DefaultWebSubscriptionSpec implements WebSubscriptionSpec {

		private final SubscriptionSpec delegate;

		private final HttpHeaders headers;

		private DefaultWebSubscriptionSpec(SubscriptionSpec delegate, @Nullable HttpHeaders headers) {
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
