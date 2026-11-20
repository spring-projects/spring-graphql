/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.interception;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClientInterceptor;

public class MyInterceptor implements GraphQlClientInterceptor {

	@Override
	public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
		// ...
		return chain.next(request);
	}

	@Override
	public Flux<ClientGraphQlResponse> interceptSubscription(ClientGraphQlRequest request, SubscriptionChain chain) {
		// ...
		return chain.next(request);
	}

}
