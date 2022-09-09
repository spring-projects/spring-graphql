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

package org.springframework.graphql.server.webflux;

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriBuilder;

/**
 * Spring WebFlux handler to serve a GraphiQl UI page.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphiQlHandler {

	private final String graphQlPath;

	private final String graphQlWsPath;

	private final Resource htmlResource;


	/**
	 * Constructor that serves the default {@code graphiql/index.html} included
	 * in the {@code spring-graphql} module.
	 * @param graphQlPath the path to the GraphQL endpoint
	 * @param graphQlWsPath optional path to the GraphQL WebSocket endpoint
	 */
	public GraphiQlHandler(String graphQlPath, String graphQlWsPath) {
		this(graphQlPath, graphQlWsPath, new ClassPathResource("graphiql/index.html"));
	}

	/**
	 * Constructor with the HTML page to serve.
	 * @param graphQlPath the path to the GraphQL endpoint
	 * @param graphQlWsPath optional path to the GraphQL WebSocket endpoint
	 * @param htmlResource the GraphiQL page to serve
	 */
	public GraphiQlHandler(String graphQlPath, String graphQlWsPath, Resource htmlResource) {
		this.graphQlPath = graphQlPath;
		this.graphQlWsPath = graphQlWsPath;
		this.htmlResource = htmlResource;
	}


	/**
	 * Render the GraphiQL page as "text/html", or if the "path" query parameter
	 * is missing, add it and redirect back to the same URL.
	 */
	public Mono<ServerResponse> handleRequest(ServerRequest request) {
		return (request.queryParam("path").isPresent() ?
				ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(this.htmlResource) :
				ServerResponse.temporaryRedirect(getRedirectUrl(request)).build());
	}

	private URI getRedirectUrl(ServerRequest request) {
		UriBuilder builder = request.uriBuilder();
		String pathQueryParam = applyContextPath(request, this.graphQlPath);
		builder.queryParam("path", pathQueryParam);

		if (StringUtils.hasText(this.graphQlWsPath)) {
			String wsPathQueryParam = applyContextPath(request, this.graphQlWsPath);
			builder.queryParam("wsPath", wsPathQueryParam);
		}
		return builder.build(request.pathVariables());
	}

	private String applyContextPath(ServerRequest request, String path) {
		String contextPath = request.requestPath().contextPath().toString();
		return StringUtils.hasText(contextPath) ? contextPath + path : path;
	}

}
