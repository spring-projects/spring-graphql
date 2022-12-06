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

package org.springframework.graphql.docs.server.interception.web;

import graphql.GraphQLContext;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;

// Subsequent access from a WebGraphQlInterceptor

class ResponseHeaderInterceptor implements WebGraphQlInterceptor {

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) { // <2>
		return chain.next(request).doOnNext(response -> {
			String value = response.getExecutionInput().getGraphQLContext().get("cookieName");
			ResponseCookie cookie = ResponseCookie.from("cookieName", value).build();
			response.getResponseHeaders().add(HttpHeaders.SET_COOKIE, cookie.toString());
		});
	}
}

@Controller
class MyCookieController {

	@QueryMapping
	Person person(GraphQLContext context) { // <1>
		context.put("cookieName", "123");
		/**/ return new Person("spring", "graphql");
	}
}
