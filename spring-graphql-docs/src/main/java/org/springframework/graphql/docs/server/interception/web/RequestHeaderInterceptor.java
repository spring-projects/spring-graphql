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

import java.util.Collections;

import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Controller;

class RequestHeaderInterceptor implements WebGraphQlInterceptor { // <1>

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		String value = request.getHeaders().getFirst("myHeader");
		request.configureExecutionInput((executionInput, builder) ->
				builder.graphQLContext(Collections.singletonMap("myHeader", value)).build());
		return chain.next(request);
	}
}

@Controller
class MyContextValueController { // <2>

	@QueryMapping
	Person person(@ContextValue String myHeader) {
		/**/ return new Person("spring", "graphql");
	}
}
