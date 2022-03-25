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


/**
 * {@link org.springframework.graphql.GraphQlResponse} implementation for server
 * handling over RSocket.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class RSocketGraphQlResponse extends DefaultExecutionGraphQlResponse {

	/**
	 * Create an instance that wraps the given {@link ExecutionGraphQlResponse}.
	 * @param response the response to wrap
	 */
	public RSocketGraphQlResponse(ExecutionGraphQlResponse response) {
		super(response);
	}

	private RSocketGraphQlResponse(RSocketGraphQlResponse original, ExecutionResult executionResult) {
		super(original.getExecutionInput(), executionResult);
	}


	/**
	 * Transform the underlying {@link ExecutionResult} through a {@link Builder}
	 * and return a new instance with the modified values.
	 * @param consumer callback to transform the result
	 * @return the new response instance with the mutated {@code ExecutionResult}
	 */
	public RSocketGraphQlResponse transform(Consumer<Builder> consumer) {
		Builder builder = new Builder(this);
		consumer.accept(builder);
		return builder.build();
	}


	/**
	 * Builder to transform a {@link RSocketGraphQlResponse}.
	 */
	public static final class Builder
			extends DefaultExecutionGraphQlResponse.Builder<Builder, RSocketGraphQlResponse> {

		private Builder(RSocketGraphQlResponse original) {
			super(original);
		}

		@Override
		protected RSocketGraphQlResponse build(RSocketGraphQlResponse original, ExecutionResult newResult) {
			return new RSocketGraphQlResponse(original, newResult);
		}

	}

}
