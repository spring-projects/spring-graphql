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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;

import org.springframework.graphql.RequestOutput;
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
public class WebOutput extends RequestOutput {

	private final HttpHeaders responseHeaders;


	/**
	 * Create an instance from the given {@link RequestOutput}.
	 * @param requestOutput the output from an executed request
	 */
	public WebOutput(RequestOutput requestOutput) {
		super(requestOutput);
		this.responseHeaders = new HttpHeaders();
	}

	private WebOutput(ExecutionInput executionInput, ExecutionResult executionResult, HttpHeaders headers) {
		super(executionInput, executionResult);
		Assert.notNull(headers, "HttpHeaders is required");
		this.responseHeaders = headers;
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

		private final ExecutionResultImpl.Builder builder;

		private Builder(WebOutput original) {
			this.original = original;
			this.builder = ExecutionResultImpl.newExecutionResult().from(original.getExecutionResult());
		}

		/**
		 * Set the {@link ExecutionResult#getData() data} of the GraphQL execution result.
		 * @param data the execution result data
		 * @return the current builder
		 */
		public Builder data(Object data) {
			this.builder.data(data);
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getErrors() errors} of the GraphQL execution
		 * result.
		 * @param errors the execution result errors
		 * @return the current builder
		 */
		public Builder errors(@Nullable List<GraphQLError> errors) {
			this.builder.errors(errors);
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getExtensions() extensions} of the GraphQL
		 * execution result.
		 * @param extensions the execution result extensions
		 * @return the current builder
		 */
		public Builder extensions(@Nullable Map<Object, Object> extensions) {
			this.builder.extensions(extensions);
			return this;
		}

		public WebOutput build() {
			return new WebOutput(this.original.getExecutionInput(), this.builder.build(),
					this.original.getResponseHeaders());
		}

	}

}
