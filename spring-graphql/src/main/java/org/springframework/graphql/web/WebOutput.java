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

package org.springframework.graphql.web;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;

/**
 * Decorate an {@link ExecutionResult}, provide a way to {@link #transform(Consumer)
 * transform} it, and collect input for custom HTTP response headers for GraphQL over HTTP
 * requests.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebOutput extends DefaultExecutionGraphQlResponse {

	private final HttpHeaders responseHeaders;


	/**
	 * Create an instance that wraps the given {@link ExecutionGraphQlResponse}.
	 * @param response the response to wrap
	 */
	public WebOutput(ExecutionGraphQlResponse response) {
		super(response);
		this.responseHeaders = new HttpHeaders();
	}

	private WebOutput(WebOutput original, ExecutionResult executionResult) {
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

		private final WebOutput original;

		private final ExecutionResultImpl.Builder executionResultBuilder;

		private Builder(WebOutput original) {
			this.original = original;
			this.executionResultBuilder = ExecutionResultImpl.newExecutionResult().from(original.getExecutionResult());
		}

		/**
		 * Set the {@link ExecutionResult#getData() data} of the GraphQL execution result.
		 * @param data the execution result data
		 * @return the current builder
		 */
		public Builder data(Object data) {
			this.executionResultBuilder.data(data);
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getErrors() errors} of the GraphQL execution
		 * result.
		 * @param errors the execution result errors
		 * @return the current builder
		 */
		public Builder errors(@Nullable List<GraphQLError> errors) {
			this.executionResultBuilder.errors(errors);
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getExtensions() extensions} of the GraphQL
		 * execution result.
		 * @param extensions the execution result extensions
		 * @return the current builder
		 */
		public Builder extensions(@Nullable Map<Object, Object> extensions) {
			this.executionResultBuilder.extensions(extensions);
			return this;
		}

		public WebOutput build() {
			return new WebOutput(this.original, this.executionResultBuilder.build());
		}

	}

}
