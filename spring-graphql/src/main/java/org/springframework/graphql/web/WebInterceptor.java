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

import java.util.List;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.GraphQlService;
import org.springframework.util.Assert;

/**
 * Interceptor for intercepting GraphQL over HTTP or WebSocket requests.
 * Provides information about the HTTP request or WebSocket handshake, allows
 * customization of the {@link ExecutionInput} and of the {@link ExecutionResult}
 * from request execution.
 *
 * <p>Interceptors may be declared as beans in Spring configuration and ordered
 * as defined in {@link ObjectProvider#orderedStream()}.
 *
 * <p>Supported for Spring MVC and WebFlux.
 */
public interface WebInterceptor {

	/**
	 * Intercept a request and delegate for further handling and request execution
	 * via {@link WebGraphQlHandler#handle(WebInput)}.
	 *
	 * @param webInput container with HTTP request information and options to
	 * customize the {@link ExecutionInput}.
	 * @param next the handler to delegate to for request execution
	 * @return a {@link Mono} with the result
	 */
	Mono<WebOutput> intercept(WebInput webInput, WebGraphQlHandler next);

	/**
	 * Return a composed {@link WebInterceptor} that invokes the current
	 * interceptor first one and then the one one passed in.
	 */
	default WebInterceptor andThen(WebInterceptor interceptor) {
		Assert.notNull(interceptor, "WebInterceptor must not be null");
		return (currentInput, next) -> intercept(currentInput, nextInput -> interceptor.intercept(nextInput, next));
	}

	/**
	 * Return {@link WebGraphQlHandler} that invokes the current interceptor
	 * first and then the given {@link GraphQlService} for actual execution of
	 * the GraphQL operation.
	 */
	default WebGraphQlHandler apply(GraphQlService service) {
		Assert.notNull(service, "GraphQlService must not be null");
		return currentInput -> intercept(currentInput, createHandler(service));
	}


	/**
	 * Factory method for a {@link WebGraphQlHandler} with a chain of
	 * interceptors followed by a {@link GraphQlService} at the end.
	 */
	static WebGraphQlHandler createHandler(List<WebInterceptor> interceptors, GraphQlService service) {
		return interceptors.stream()
				.reduce(WebInterceptor::andThen)
				.map(interceptor -> interceptor.apply(service))
				.orElse(createHandler(service));
	}

	/**
	 * Factory method for a {@link WebGraphQlHandler} that simple invokes the
	 * given {@link GraphQlService} adapting to its input and output.
	 */
	static WebGraphQlHandler createHandler(GraphQlService graphQlService) {
		Assert.notNull(graphQlService, "GraphQlService must not be null");
		return webInput -> {
			ExecutionInput executionInput = webInput.toExecutionInput();
			return graphQlService.execute(executionInput).map(result -> new WebOutput(webInput, result));
		};
	}

}