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

package org.springframework.graphql;

import reactor.core.publisher.Mono;

/**
 * Strategy to execute a GraphQL request.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlService {

	/**
	 * Execute the GraphQL request and return the result.
	 * @param input container for GraphQL request input
	 * @return the result from execution
	 */
	Mono<RequestOutput> execute(RequestInput input);

}
