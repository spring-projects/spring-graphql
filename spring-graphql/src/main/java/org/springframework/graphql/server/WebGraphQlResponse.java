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

package org.springframework.graphql.server;

import java.util.function.Consumer;

import graphql.ExecutionResult;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;


/**
 * {@link org.springframework.graphql.GraphQlResponse} implementation for server
 * handling over HTTP or over WebSocket.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebGraphQlResponse extends DefaultExecutionGraphQlResponse {

	private final HttpHeaders responseHeaders;


	/**
	 * Create an instance that wraps the given {@link ExecutionGraphQlResponse}.
	 * @param response the response to wrap
	 */
	public WebGraphQlResponse(ExecutionGraphQlResponse response) {
		super(response);
		this.responseHeaders = new HttpHeaders();
	}

	private WebGraphQlResponse(WebGraphQlResponse original, ExecutionResult executionResult) {
		super(original.getExecutionInput(), executionResult);
		this.responseHeaders = original.getResponseHeaders();
	}


	/**
	 * Return the headers to be added to the HTTP response.
	 * <p>By default, this is empty.
	 * <p><strong>Note:</strong> This is for use with GraphQL over HTTP requests
	 * but not for GraphQL over WebSocket where the initial handshake HTTP
	 * request completes before queries begin.
	 */
	public HttpHeaders getResponseHeaders() {
		return this.responseHeaders;
	}

	/**
	 * Transform the underlying {@link ExecutionResult} through a {@link Builder}
	 * and return a new instance with the modified values.
	 * @param consumer callback to transform the result
	 * @return the new response instance with the mutated {@code ExecutionResult}
	 */
	public WebGraphQlResponse transform(Consumer<Builder> consumer) {
		Builder builder = new Builder(this);
		consumer.accept(builder);
		return builder.build();
	}


	/**
	 * Builder to transform a {@link WebGraphQlResponse}.
	 */
	public static final class Builder extends DefaultExecutionGraphQlResponse.Builder<Builder, WebGraphQlResponse> {

		private Builder(WebGraphQlResponse original) {
			super(original);
		}

		@Override
		protected WebGraphQlResponse build(WebGraphQlResponse original, ExecutionResult newResult) {
			return new WebGraphQlResponse(original, newResult);
		}

	}

}
