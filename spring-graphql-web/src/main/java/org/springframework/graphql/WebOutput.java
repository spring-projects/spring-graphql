/*
 * Copyright 2020-2020 the original author or authors.
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
package org.springframework.graphql;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;

import org.springframework.lang.Nullable;


/**
 * {@link ExecutionResult} that wraps another in order to provide a convenient
 * way to {@link #transform(Consumer) transform} it.
 */
public class WebOutput implements ExecutionResult {

	private final ExecutionResult executionResult;


	/**
	 * Create an instance that wraps the given {@link ExecutionResult}.
	 */
	public WebOutput(ExecutionResult executionResult) {
		this.executionResult = executionResult;
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
	 * Transform this {@code WebOutput} instance through a {@link Builder} and
	 * return a new instance with the modified values.
	 */
	public WebOutput transform(Consumer<Builder> consumer) {
		Builder builder = new Builder(this);
		consumer.accept(builder);
		return builder.build();
	}


	public static class Builder {

		@Nullable
		private Object data;

		private List<GraphQLError> errors;

		@Nullable
		private Map<Object, Object> extensions;

		public Builder(WebOutput output) {
			this.data = output.getData();
			this.errors = output.getErrors();
			this.extensions = output.getExtensions();
		}

		/**
		 * Set the execution {@link ExecutionResult#getData() data}.
		 */
		public Builder data(@Nullable Object data) {
			this.data = data;
			return this;
		}

		/**
		 * Set the execution {@link ExecutionResult#getErrors() errors}.
		 */
		public Builder errors(@Nullable List<GraphQLError> errors) {
			this.errors = (errors != null ? errors : Collections.emptyList());
			return this;
		}

		/**
		 * Set the execution {@link ExecutionResult#getExtensions() extensions}.
		 */
		public Builder extensions(@Nullable Map<Object, Object> extensions) {
			this.extensions = extensions;
			return this;
		}

		public WebOutput build() {
			return new WebOutput(new ExecutionResultImpl(this.data, this.errors, this.extensions));
		}
	}

}
