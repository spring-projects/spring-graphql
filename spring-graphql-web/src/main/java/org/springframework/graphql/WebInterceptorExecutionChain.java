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
import java.util.concurrent.CompletableFuture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Supports the use of {@link WebInterceptor}s to customize the
 * {@link ExecutionInput} and the {@link ExecutionResult} of {@link GraphQL}
 * query execution.
 */
class WebInterceptorExecutionChain {

	private final GraphQL graphQL;

	private final List<WebInterceptor> interceptors;


	WebInterceptorExecutionChain(GraphQL graphQL, List<WebInterceptor> interceptors) {
		Assert.notNull(graphQL, "GraphQL is required");
		this.graphQL = graphQL;
		this.interceptors = (!CollectionUtils.isEmpty(interceptors) ?
				Collections.unmodifiableList(new ArrayList<>(interceptors)) : Collections.emptyList());
	}


	public Mono<WebOutput> execute(WebInput input) {
		return createInputChain(input).flatMap(executionInput -> {
			CompletableFuture<ExecutionResult> future = this.graphQL.executeAsync(executionInput);
			return createOutputChain(input, Mono.fromFuture(future));
		});
	}

	private Mono<ExecutionInput> createInputChain(WebInput webInput) {
		Mono<ExecutionInput> preHandleMono = Mono.just(webInput.toExecutionInput());
		for (WebInterceptor interceptor : this.interceptors) {
			preHandleMono = preHandleMono.flatMap(input -> interceptor.preHandle(input, webInput));
		}
		return preHandleMono;
	}

	private Mono<WebOutput> createOutputChain(WebInput input, Mono<ExecutionResult> resultMono) {
		Mono<WebOutput> outputMono = resultMono.map((ExecutionResult result) -> new WebOutput(input, result, null));
		for (int i = this.interceptors.size() - 1 ; i >= 0; i--) {
			WebInterceptor interceptor = this.interceptors.get(i);
			outputMono = outputMono.flatMap(interceptor::postHandle);
		}
		return outputMono;
	}

}
