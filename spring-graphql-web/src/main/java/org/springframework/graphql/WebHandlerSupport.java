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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

import org.springframework.util.CollectionUtils;

/**
 * Base class for GraphQL over HTTP handlers.
 */
public abstract class WebHandlerSupport {

	private final GraphQL graphQL;

	private final List<WebInterceptor> interceptors;


	public WebHandlerSupport(GraphQL.Builder builder, List<WebInterceptor> interceptors) {
		this.graphQL = builder.build();
		this.interceptors = (!CollectionUtils.isEmpty(interceptors) ?
				Collections.unmodifiableList(new ArrayList<>(interceptors)) : Collections.emptyList());
	}


	public GraphQL getGraphQL() {
		return this.graphQL;
	}

	public List<WebInterceptor> getInterceptors() {
		return this.interceptors;
	}


	protected Mono<WebOutput> executeQuery(WebInput webInput) {
		return createInputChain(webInput).flatMap(executionInput -> {
			Mono<ExecutionResult> resultMono = Mono.fromFuture(getGraphQL().executeAsync(executionInput));
			return createOutputChain(resultMono);
		});
	}

	protected Mono<ExecutionInput> createInputChain(WebInput webInput) {
		Mono<ExecutionInput> preHandleMono = Mono.just(webInput.toExecutionInput());
		for (WebInterceptor interceptor : this.interceptors) {
			preHandleMono = preHandleMono.flatMap(input -> interceptor.preHandle(input, webInput));
		}
		return preHandleMono;
	}

	protected Mono<WebOutput> createOutputChain(Mono<ExecutionResult> resultMono) {
		Mono<WebOutput> outputMono = resultMono.map(WebOutput::new);
		for (WebInterceptor interceptor : this.interceptors) {
			outputMono = outputMono.flatMap(interceptor::postHandle);
		}
		return outputMono;
	}

}
