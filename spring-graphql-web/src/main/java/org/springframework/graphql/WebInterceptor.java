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
import graphql.ExecutionResult;
import reactor.core.publisher.Mono;

import org.springframework.graphql.webmvc.GraphQLHttpHandler;

/**
 * Web interceptor for GraphQL queries over HTTP. The interceptor allows
 * customization of the {@link ExecutionInput} for the query as well as the
 * {@link ExecutionResult} of the query and is supported for both Spring MVC and
 * Spring WebFlux.
 *
 * <p>A list of interceptors may be provided to {@link GraphQLHttpHandler} or
 * to {@link org.springframework.graphql.webflux.GraphQLHttpHandler}. Interceptors are executed in that provided
 * order where each interceptor sees the {@code ExecutionInput} or the
 * {@code ExecutionResult} that was customized by the previous interceptor.
 */
public interface WebInterceptor {

	/**
	 * Intercept a GraphQL over HTTP request before the query is executed.
	 *
	 * <p>{@code ExecutionInput} is initially populated with the input from the
	 * request body via {@link WebInput#toExecutionInput()} where the
	 * {@link WebInput#getQuery() query} is guaranteed to be a non-empty String.
	 * Interceptors are then executed in order to further customize the input
	 * and or perform other actions or checks.
	 *
	 * @param executionInput the input to use, initialized from {@code WebInput}
	 * @param webInput the input from the HTTP request
	 * @return the same instance or a new one via {@link ExecutionInput#transform(Consumer)}
	 */
	default Mono<ExecutionInput> preHandle(ExecutionInput executionInput, WebInput webInput) {
		return Mono.just(executionInput);
	}

	/**
	 * Intercept a GraphQL over HTTP request after the query is executed.
	 *
	 * <p>{@code WebOutput} initially wraps the {@link ExecutionResult} returned
	 * from the execution of the query. Interceptors are then executed in order
	 * to further customize it and/or perform other actions.
	 *
	 * @param webOutput the execution result
	 * @return the same instance or a new one via {@link WebOutput#transform(Consumer)}
	 */
	default Mono<WebOutput> postHandle(WebOutput webOutput) {
		return Mono.just(webOutput);
	}

}