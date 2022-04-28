/*
 * Copyright 2002-2022 the original author or authors.
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
package org.springframework.graphql.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.ErrorClassification;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link GraphQlResponse} for server use that wraps the {@link ExecutionResult}
 * returned from {@link graphql.GraphQL} and also exposes the actual
 * {@link ExecutionInput} instance passed into it.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultExecutionGraphQlResponse extends AbstractGraphQlResponse implements ExecutionGraphQlResponse {

	private final ExecutionInput input;

	private final ExecutionResult result;


	/**
	 * Constructor to create initial instance.
	 */
	public DefaultExecutionGraphQlResponse(ExecutionInput input, ExecutionResult result) {
		Assert.notNull(input, "ExecutionInput is required");
		Assert.notNull(result, "ExecutionResult is required");
		this.input = input;
		this.result = result;
	}

	/**
	 * Constructor to re-wrap from transport specific subclass.
	 */
	protected DefaultExecutionGraphQlResponse(ExecutionGraphQlResponse response) {
		this(response.getExecutionInput(), response.getExecutionResult());
	}


	@Override
	public ExecutionInput getExecutionInput() {
		return this.input;
	}

	@Override
	public ExecutionResult getExecutionResult() {
		return this.result;
	}

	@Override
	public boolean isValid() {
		return (this.result.isDataPresent() && this.result.getData() != null);
	}

	@Nullable
	@Override
	public <T> T getData() {
		return this.result.getData();
	}

	@Override
	public List<ResponseError> getErrors() {
		return this.result.getErrors().stream().map(Error::new).collect(Collectors.toList());
	}

	@Override
	public Map<Object, Object> getExtensions() {
		return (this.result.getExtensions() != null ? this.result.getExtensions() : Collections.emptyMap());
	}

	@Override
	public Map<String, Object> toMap() {
		return this.result.toSpecification();
	}

	@Override
	public String toString() {
		return this.result.toString();
	}


	/**
	 * {@link GraphQLError} that wraps a {@link GraphQLError}.
	 */
	private static class Error implements ResponseError {

		private final GraphQLError delegate;

		Error(GraphQLError delegate) {
			this.delegate = delegate;
		}

		@Override
		public String getMessage() {
			return this.delegate.getMessage();
		}

		@Override
		public List<SourceLocation> getLocations() {
			return this.delegate.getLocations();
		}

		@Override
		public ErrorClassification getErrorType() {
			return this.delegate.getErrorType();
		}

		@Override
		public String getPath() {
			return getParsedPath().stream()
					.reduce("",
							(s, o) -> s + (o instanceof Integer ? "[" + o + "]" : (s.isEmpty() ? o : "." + o)),
							(s, s2) -> null);
		}

		@Override
		public List<Object> getParsedPath() {
			return (this.delegate.getPath() != null ? this.delegate.getPath() : Collections.emptyList());
		}

		@Override
		public Map<String, Object> getExtensions() {
			return (this.delegate.getExtensions() != null ? this.delegate.getExtensions() : Collections.emptyMap());
		}

		@Override
		public String toString() {
			return this.delegate.toString();
		}

	}


	/**
	 * Builder to transform the response's {@link ExecutionResult}.
	 */
	public static abstract class Builder<B extends Builder<B, R>, R extends ExecutionGraphQlResponse> {

		private final R original;

		private final ExecutionResultImpl.Builder executionResultBuilder;

		protected Builder(R original) {
			this.original = original;
			this.executionResultBuilder = ExecutionResultImpl.newExecutionResult().from(original.getExecutionResult());
		}

		/**
		 * Set the {@link ExecutionResult#getData() data} of the GraphQL execution result.
		 * @param data the execution result data
		 * @return the current builder
		 */
		public Builder<B, R> data(Object data) {
			this.executionResultBuilder.data(data);
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getErrors() errors} of the GraphQL execution
		 * result.
		 * @param errors the execution result errors
		 * @return the current builder
		 */
		public Builder<B, R> errors(@Nullable List<GraphQLError> errors) {
			this.executionResultBuilder.errors(errors);
			return this;
		}

		/**
		 * Set the {@link ExecutionResult#getExtensions() extensions} of the GraphQL
		 * execution result.
		 * @param extensions the execution result extensions
		 * @return the current builder
		 */
		public Builder<B, R> extensions(@Nullable Map<Object, Object> extensions) {
			this.executionResultBuilder.extensions(extensions);
			return this;
		}

		/**
		 * Build the response with the transformed {@code ExecutionResult}.
		 */
		public R build() {
			return build(this.original, this.executionResultBuilder.build());
		}

		/**
		 * Subclasses to create the specific response instance.
		 */
		protected abstract R build(R original, ExecutionResult newResult);

	}

}
