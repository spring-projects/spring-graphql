/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
		return chain.next(request).doOnNext((response) -> {
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
