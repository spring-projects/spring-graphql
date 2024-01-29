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

package org.springframework.graphql.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


/**
 * Interceptor for {@link GraphQlClient} requests for use in a non-blocking
 * execution chain with a non-blocking {@link GraphQlTransport}..
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see GraphQlClient.Builder
 */
public interface GraphQlClientInterceptor {

	/**
	 * Intercept a single response request (query and mutation operations) and
	 * delegate to the rest of the chain including other interceptors followed
	 * by the {@link GraphQlTransport}.
	 * @param request the request to perform
	 * @param chain the rest of the chain to perform the request
	 * @return a {@link Mono} for the response
	 * @see GraphQlClient.RequestSpec#execute()
	 */
	default Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
		return chain.next(request);
	}

	/**
	 * Intercept a subscription request and delegate to the rest of the chain
	 * including other interceptors followed by the {@link GraphQlTransport}.
	 * @param request the request to perform
	 * @param chain the rest of the chain to perform the request
	 * @return a {@link Flux} with responses
	 * @see GraphQlClient.RequestSpec#executeSubscription()
	 */
	default Flux<ClientGraphQlResponse> interceptSubscription(ClientGraphQlRequest request, SubscriptionChain chain) {
		return chain.next(request);
	}

	/**
	 * Return a new interceptor that invokes the current interceptor first and
	 * then the one that is passed in.
	 * @param interceptor the interceptor to delegate to after "this"
	 * @return the new {@code GraphQlClientInterceptor}
	 */
	default GraphQlClientInterceptor andThen(GraphQlClientInterceptor interceptor) {
		return new GraphQlClientInterceptor() {

			@Override
			public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
				return GraphQlClientInterceptor.this.intercept(
						request, nextRequest -> interceptor.intercept(nextRequest, chain));
			}

			@Override
			public Flux<ClientGraphQlResponse> interceptSubscription(ClientGraphQlRequest request, SubscriptionChain chain) {
				return GraphQlClientInterceptor.this.interceptSubscription(
						request, nextRequest -> interceptor.interceptSubscription(nextRequest, chain));
			}
		};
	}


	/**
	 * Contract to delegate to the rest of a non-blocking execution chain.
	 */
	interface Chain {

		/**
		 * Delegate to the rest of the chain to perform the request.
		 * @param request the request to perform
		 * @return {@code Mono} with the response
		 * @see GraphQlClient.RequestSpec#execute()
		 */
		Mono<ClientGraphQlResponse> next(ClientGraphQlRequest request);

	}


	/**
	 * Contract for delegation of subscription requests to the rest of the chain.
	 */
	interface SubscriptionChain {

		/**
		 * Delegate to the rest of the chain to perform the request.
		 * @param request the request to perform
		 * @return {@code Flux} with responses
		 * @see GraphQlClient.RequestSpec#executeSubscription()
		 */
		Flux<ClientGraphQlResponse> next(ClientGraphQlRequest request);

	}

}
