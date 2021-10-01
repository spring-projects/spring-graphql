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

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import graphql.GraphQLError;

import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;

/**
 * Default implementation of a {@link WebGraphQlTester.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultWebGraphQlTesterBuilder
		extends GraphQlTesterBuilderSupport implements WebGraphQlTester.Builder {

	@Nullable
	private final WebTestClient client;

	@Nullable
	private final WebGraphQlHandler handler;

	@Nullable
	private HttpHeaders headers;


	DefaultWebGraphQlTesterBuilder(WebTestClient client) {
		Assert.notNull(client, "WebTestClient is required.");
		this.client = client;
		this.handler = null;
	}

	DefaultWebGraphQlTesterBuilder(WebGraphQlHandler handler) {
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
	public DefaultWebGraphQlTesterBuilder jsonPathConfig(Configuration config) {
		setJsonPathConfig(config);
		return this;
	}

	@Override
	public DefaultWebGraphQlTesterBuilder responseTimeout(Duration timeout) {
		setResponseTimeout(timeout);
		return this;
	}

	@Override
	public DefaultWebGraphQlTesterBuilder defaultHeader(String headerName, String... headerValues) {
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
		return new DefaultWebGraphQlTester(initRequestStrategy(), this.headers);
	}

	private WebRequestStrategy initRequestStrategy() {
		if (this.client != null) {
			WebTestClient clientToUse = this.client;
			if (getResponseTimeout() != null) {
				clientToUse = this.client.mutate().responseTimeout(getResponseTimeout()).build();
			}
			return new WebTestClientRequestStrategy(
					clientToUse, getErrorFilter(), initJsonPathConfig(), getResponseTimeout());
		}

		if (this.handler != null) {
			return new WebGraphQlHandlerRequestStrategy(
					this.handler, getErrorFilter(), initJsonPathConfig(), initResponseTimeout());
		}

		throw new IllegalStateException("Neither client nor handler");
	}

}
