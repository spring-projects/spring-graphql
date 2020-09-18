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

import java.util.function.Consumer;

import graphql.ExecutionInput;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

/**
 * Interceptor for GraphQL over HTTP requests that allows customization of the
 * {@link ExecutionInput} and the {@link graphql.ExecutionResult} of
 * {@link GraphQL} query execution.
 */
public interface WebInterceptor {

	/**
	 * Intercept a GraphQL over HTTP request before the query is executed.
	 *
	 * @param executionInput the input to use, initialized from the {@code WebInput}
	 * @param webInput the input from the HTTP request
	 * @return the same instance or a new one via {@link ExecutionInput#transform(Consumer)}
	 */
	default Mono<ExecutionInput> preHandle(ExecutionInput executionInput, WebInput webInput) {
		return Mono.just(executionInput);
	}

	/**
	 * Intercept a GraphQL over HTTP request after the query is executed.
	 *
	 * @param webOutput the execution result
	 * @return the same instance or a new one via {@link WebOutput#transform(Consumer)}
	 */
	default Mono<WebOutput> postHandle(WebOutput webOutput) {
		return Mono.just(webOutput);
	}

}