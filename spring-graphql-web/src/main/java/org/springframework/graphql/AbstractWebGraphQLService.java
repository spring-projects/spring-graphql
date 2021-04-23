/*
 * Copyright 2002-2021 the original author or authors.
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import reactor.core.publisher.Mono;

import org.springframework.graphql.core.ReactorDataFetcherAdapter;

/**
 * Base class for {@link WebGraphQLService} implementations, providing support
 * for customizations of the request through a {@link WebInterceptor} chain.
 * Sub-classes must implement {@link #executeInternal(ExecutionInput)} to
 * actually perform the GraphQL query.
 */
public abstract class AbstractWebGraphQLService implements WebGraphQLService {

	private final List<WebInterceptor> interceptors = new ArrayList<>();


	/**
	 * Set the interceptors to invoke to handle request.
	 * @param interceptors the interceptors to use
	 */
	public void setInterceptors(List<WebInterceptor> interceptors) {
		this.interceptors.clear();
		this.interceptors.addAll(interceptors);
	}

	/**
	 * Return the {@link #setInterceptors(List) configured} interceptors.
	 */
	public List<WebInterceptor> getInterceptors() {
		return this.interceptors;
	}


	@Override
	public final Mono<WebOutput> execute(WebInput input) {
		return preHandle(input)
				.flatMap(executionInput -> Mono.fromFuture(executeInternal(executionInput)))
				.flatMap(executionResult -> postHandle(new WebOutput(input, executionResult)));
	}

	private Mono<ExecutionInput> preHandle(WebInput input) {
		Mono<ExecutionInput> resultMono = Mono.deferContextual(contextView -> {
			ExecutionInput executionInput = input.toExecutionInput();
			ReactorDataFetcherAdapter.addReactorContext(executionInput, contextView);
			return Mono.just(executionInput);
		});
		for (WebInterceptor interceptor : this.interceptors) {
			resultMono = resultMono.flatMap(executionInput -> interceptor.preHandle(executionInput, input));
		}
		return resultMono;
	}

	private Mono<WebOutput> postHandle(WebOutput output) {
		Mono<WebOutput> outputMono = Mono.just(output);
		for (int i = this.interceptors.size() - 1 ; i >= 0; i--) {
			WebInterceptor interceptor = this.interceptors.get(i);
			outputMono = outputMono.flatMap(interceptor::postHandle);
		}
		return outputMono;
	}

	/**
	 * Sub-classes must implement this method to actually handle the request.
	 * @param input the input to invoke {@link graphql.GraphQL} with
	 * @return the result from handling
	 */
	protected abstract CompletableFuture<ExecutionResult> executeInternal(ExecutionInput input);

}
