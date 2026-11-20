/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.server.interception.web;

import java.util.List;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;

class RequestErrorInterceptor implements WebGraphQlInterceptor {

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		return chain.next(request).map((response) -> {
			if (response.isValid()) {
				return response; // <1>
			}

			List<GraphQLError> errors = response.getErrors().stream() // <2>
					.map((error) -> {
						GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError();
						// ...
						return builder.build();
					})
					.toList();

			return response.transform((builder) -> builder.errors(errors).build()); // <3>
		});
	}
}
