/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.webflux;

import java.net.URI;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Spring WebFlux handler to serve a GraphiQl UI page.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphiQlHandler {

	private final String graphQlPath;

	private final @Nullable String graphQlWsPath;

	private final Resource htmlResource;


	/**
	 * Constructor that serves the default {@code graphiql/index.html} included
	 * in the {@code spring-graphql} module.
	 * @param graphQlPath the path to the GraphQL endpoint
	 * @param graphQlWsPath optional path to the GraphQL WebSocket endpoint
	 */
	public GraphiQlHandler(String graphQlPath, @Nullable String graphQlWsPath) {
		this(graphQlPath, graphQlWsPath, new ClassPathResource("graphiql/index.html"));
	}

	/**
	 * Constructor with the HTML page to serve.
	 * @param graphQlPath the path to the GraphQL endpoint
	 * @param graphQlWsPath optional path to the GraphQL WebSocket endpoint
	 * @param htmlResource the GraphiQL page to serve
	 */
	public GraphiQlHandler(String graphQlPath, @Nullable String graphQlWsPath, Resource htmlResource) {
		Assert.hasText(graphQlPath, "graphQlPath should not be empty");
		Assert.notNull(htmlResource, "HtmlResource is required");
		this.graphQlPath = graphQlPath;
		this.graphQlWsPath = graphQlWsPath;
		this.htmlResource = htmlResource;
	}


	/**
	 * Render the GraphiQL page as "text/html", or if the "path" query parameter
	 * is missing, add it and redirect back to the same URL.
	 * @param request the HTTP server request
	 */
	public Mono<ServerResponse> handleRequest(ServerRequest request) {
		if (request.queryParam("path").isPresent()) {
			return serveGraphiQlPage(request);
		}
		else {
			return ServerResponse.temporaryRedirect(getRedirectUrl(request)).build();
		}
	}

	private Mono<ServerResponse> serveGraphiQlPage(ServerRequest request) {
		if (!isExpectedPath(request, "path", this.graphQlPath) ||
				!isExpectedPath(request, "wsPath", this.graphQlWsPath)) {
			return ServerResponse.badRequest().build();
		}
		return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(this.htmlResource);
	}

	/**
	 * Check that the given query parameter, if present, is exactly the
	 * same-origin relative path that this handler would itself generate for
	 * a redirect (see {@link #getRedirectUrl}). This avoids having to parse
	 * and validate an arbitrary, possibly absolute, client-supplied URL.
	 */
	private boolean isExpectedPath(ServerRequest request, String paramName, @Nullable String configuredPath) {
		return request.queryParam(paramName)
				.map((value) -> StringUtils.hasText(configuredPath) && value.equals(expandPath(request, configuredPath)))
				.orElse(true);
	}

	private String expandPath(ServerRequest request, String path) {
		String pathWithContext = applyContextPath(request, path);
		return UriComponentsBuilder.fromPath(pathWithContext).build().expand(request.pathVariables()).toUriString();
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
