/*
 * Copyright 2002-2022 the original author or authors.
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

import graphql.ExecutionInput;
import reactor.core.publisher.Mono;

/**
 * Allows a {@link WebInterceptor} to invoke the rest of the chain.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebInterceptorChain {

	/**
	 * Delegate to the rest of the chain that consists of other interceptors
	 * followed by a {@link org.springframework.graphql.GraphQlService} that
	 * executes the request through the GraphQL Java.
	 * @param request provides access to GraphQL request and allows customizing
	 * the {@link ExecutionInput} for {@link graphql.GraphQL}.
	 * @return {@code Mono} with the response
	 */
	Mono<WebGraphQlResponse> next(WebGraphQlRequest request);

}
