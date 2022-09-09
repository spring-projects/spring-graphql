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

package org.springframework.graphql.server.webmvc;

import java.net.URI;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriBuilder;

/**
 * Spring MVC handler to serve a GraphiQl UI page.
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
	 * @param graphQlPath the path to the GraphQL HTTP endpoint
	 * @param graphQlWsPath optional path to the GraphQL WebSocket endpoint
	 */
	public GraphiQlHandler(String graphQlPath, String graphQlWsPath) {
		this(graphQlPath, graphQlWsPath, new ClassPathResource("graphiql/index.html"));
	}

	/**
	 * Constructor with the HTML page to serve.
	 * @param graphQlPath the path to the GraphQL HTTP endpoint
	 * @param graphQlWsPath optional path to the GraphQL WebSocket endpoint
	 * @param htmlResource the GraphiQL page to serve
	 */
	public GraphiQlHandler(String graphQlPath, String graphQlWsPath, Resource htmlResource) {
		Assert.hasText(graphQlPath, "graphQlPath should not be empty");
		this.graphQlPath = graphQlPath;
		this.graphQlWsPath = graphQlWsPath;
		this.htmlResource = htmlResource;
	}


	/**
	 * Render the GraphiQL page as "text/html", or if the "path" query parameter
	 * is missing, add it and redirect back to the same URL.
	 */
	public ServerResponse handleRequest(ServerRequest request) {
		return (request.param("path").isPresent() ?
				ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(this.htmlResource) :
				ServerResponse.temporaryRedirect(getRedirectUrl(request)).build());
	}

	private URI getRedirectUrl(ServerRequest request) {
		UriBuilder builder = request.uriBuilder();
		String pathQueryParam = applyPathPrefix(request, this.graphQlPath);
		builder.queryParam("path", pathQueryParam);
		if (StringUtils.hasText(this.graphQlWsPath)) {
			String wsPathQueryParam = applyPathPrefix(request, this.graphQlWsPath);
			builder.queryParam("wsPath", wsPathQueryParam);
		}
		return builder.build(request.pathVariables());
	}

	private String applyPathPrefix(ServerRequest request, String path) {
		String fullPath = request.requestPath().value();
		String pathWithinApplication = request.requestPath().pathWithinApplication().toString();
		int pathWithinApplicationIndex = fullPath.indexOf(pathWithinApplication);
		return (pathWithinApplicationIndex != -1) ? fullPath.substring(0, pathWithinApplicationIndex) + path : path;
	}

}
