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

package org.springframework.graphql.observation;

import io.micrometer.tracing.propagation.Propagator;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

/**
 * {@link WebGraphQlInterceptor} that copies {@link Propagator propagation} headers
 * from the HTTP request to the {@link graphql.GraphQLContext}.
 * This makes it possible to propagate tracing information sent by HTTP clients.
 *
 * @author Brian Clozel
 * @since 1.1.1
 */
public class PropagationWebGraphQlInterceptor implements WebGraphQlInterceptor {

	private final Propagator propagator;

	/**
	 * Create an interceptor that leverages the field names used by the given
	 * {@link Propagator} instance.
	 *
	 * @param propagator the propagator that will be used for tracing support
	 */
	public PropagationWebGraphQlInterceptor(Propagator propagator) {
		Assert.notNull(propagator, "propagator should not be null");
		this.propagator = propagator;
	}

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		request.configureExecutionInput((input, inputBuilder) -> {
			HttpHeaders headers = request.getHeaders();
			for (String field : this.propagator.fields()) {
				if (headers.containsKey(field)) {
					inputBuilder.graphQLContext(contextBuilder -> contextBuilder.of(field, headers.getFirst(field)));
				}
			}
			return inputBuilder.build();
		});
		return chain.next(request);
	}

}
