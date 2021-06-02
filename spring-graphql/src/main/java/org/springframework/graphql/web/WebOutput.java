/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.web;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Decorate an {@link ExecutionResult}, provide a way to {@link #transform(Consumer)
 * transform} it, and collect input for custom HTTP response headers for GraphQL over HTTP
 * requests.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebOutput implements ExecutionResult {

	private final WebInput input;

	private final ExecutionResult executionResult;

	@Nullable
	private final HttpHeaders responseHeaders;

	/**
	 * Create an instance that wraps the given {@link ExecutionResult}.
	 * @param input the container for the GraphQL input
	 * @param executionResult the result of performing a graphql query
	 */
	public WebOutput(WebInput input, ExecutionResult executionResult) {
		this(input, executionResult, null);
	}

	private WebOutput(WebInput input, ExecutionResult executionResult, @Nullable HttpHeaders responseHeaders) {
		Assert.notNull(input, "WebInput is required.");
		Assert.notNull(executionResult, "ExecutionResult is required.");
		this.input = input;
		this.executionResult = executionResult;
		this.responseHeaders = responseHeaders;
	}

	/**
	 * Return the associated {@link WebInput} used for the execution.
	 * @return the associated WebInput
	 */
	public WebInput getWebInput() {
		return this.input;
	}

	@Nullable
	@Override
	public <T> T getData() {
		return this.executionResult.getData();
	}

	@Override
	public boolean isDataPresent() {
		return this.executionResult.isDataPresent();
	}

	public List<GraphQLError> getErrors() {
		return this.executionResult.getErrors();
	}

	@Nullable
	public Map<Object, Object> getExtensions() {
		return this.executionResult.getExtensions();
	}

	@Override
	public Map<String, Object> toSpecification() {
		return this.executionResult.toSpecification();
	}

	/**
	 * Return a read-only view of any custom headers to be added to the HTTP response, or
	 * {@code null} until {@link #transform(Consumer)} is used to add such headers.
	 * @return the read-only HTTP response headers
	 * @see #transform(Consumer)
	 * @see Builder#responseHeader(String, String...)
	 */
	@Nullable
	public HttpHeaders getResponseHeaders() {
		return (this.responseHeaders != null) ? HttpHeaders.readOnlyHttpHeaders(this.responseHeaders) : null;
	}

	/**
	 * Transform this {@code WebOutput} instance through a {@link Builder} and return a
	 * new instance with the modified values.
	 * @param consumer teh callback that will transform the WebOutput
	 * @return the transformed WebOutput
	 */
	public WebOutput transform(Consumer<Builder> consumer) {
		Builder builder = new Builder(this);
		consumer.accept(builder);
		return builder.build();
	}

	/**
	 * Builder to transform a {@link WebOutput}.
	 */
	public static final class Builder {

		private final WebInput input;

		@Nullable
		private Object data;

		private List<GraphQLError> errors;

		@Nullable
		private Map<Object, Object> extensions;

		@Nullable
		private HttpHeaders headers;

		private Builder(WebOutput output) {
			this.input = output.getWebInput();
			this.data = output.getData();
			this.errors = output.getErrors();
			this.extensions = output.getExtensions();
			this.headers = output.responseHeaders;
		}

		/**
		 * Set the {@link ExecutionResult#getData() data} of the GraphQL execution result.
		 * @param data the execution result data
		 * @return the current builder
		 */
		public Builder data(@Nullable Object data) {
			this.data = data;
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getErrors() errors} of the GraphQL execution
		 * result.
		 * @param errors the execution result errors
		 * @return the current builder
		 */
		public Builder errors(@Nullable List<GraphQLError> errors) {
			this.errors = (errors != null) ? errors : Collections.emptyList();
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getExtensions() extensions} of the GraphQL
		 * execution result.
		 * @param extensions the execution result extensions
		 * @return the current builder
		 */
		public Builder extensions(@Nullable Map<Object, Object> extensions) {
			this.extensions = extensions;
			return this;
		}

		/**
		 * Add a custom header to be set on the HTTP response.
		 *
		 * <p>
		 * <strong>Note:</strong> This can be used for GraphQL over HTTP requests but has
		 * no impact for queries over a WebSocket session where the initial handshake
		 * request completes before queries begin.
		 * @param name the HTTP header name
		 * @param values the HTTP header values
		 * @return the current builder
		 */
		public Builder responseHeader(String name, String... values) {
			initHeaders();
			for (String value : values) {
				this.headers.add(name, value);
			}
			return this;
		}

		/**
		 * Consume and update the headers to be set on the HTTP response.
		 *
		 * <p>
		 * <strong>Note:</strong> This can be used for GraphQL over HTTP requests but has
		 * no impact for queries over a WebSocket session where the initial handshake
		 * request completes before queries begin.
		 * @param consumer callback that updates the HTTP headers
		 * @return the current builder
		 */
		public Builder responseHeaders(Consumer<HttpHeaders> consumer) {
			initHeaders();
			consumer.accept(this.headers);
			return this;
		}

		private void initHeaders() {
			this.headers = (this.headers != null) ? this.headers : new HttpHeaders();
		}

		public WebOutput build() {
			ExecutionResult result = new ExecutionResultImpl(this.data, this.errors, this.extensions);
			return new WebOutput(this.input, result, this.headers);
		}

	}

}
