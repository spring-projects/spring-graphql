/*
 * Copyright 2020-present the original author or authors.
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
