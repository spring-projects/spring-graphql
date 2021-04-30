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
package org.springframework.graphql.web;

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQLService;

/**
 * Contract to handle a GraphQL over HTTP or WebSocket request that forms the
 * basis of a {@link WebInterceptor} delegation chain.
 *
 * @see WebInterceptor#createHandler(List, GraphQLService)
 */
public interface WebGraphQLHandler {

	/**
	 * Perform query execution for the given request and return the result.
	 *
	 * @param input the GraphQL query container
	 * @return the execution result
	 */
	Mono<WebOutput> handle(WebInput input);

}
