/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.web.webmvc;

import java.net.URI;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Spring MVC functional handler that renders a GraphiQl UI page.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @author Janne Valkealahti
 */
public class GraphiQlHandler {

	private final Resource graphiQlHtmlResource;

	private final Resource graphiQlJsResource;

	private final Map<String, String> graphiQlConfig;

	/**
	 * Create an instance.
	 * @param graphiQlHtmlResource the GraphiQL page
	 * @param graphiQlJsResource the GraphiQL JS
	 * @param graphiQlConfig the config map translating to JS
	 */
	public GraphiQlHandler(Resource graphiQlHtmlResource, Resource graphiQlJsResource,
			Map<String, String> graphiQlConfig) {
		this.graphiQlHtmlResource = graphiQlHtmlResource;
		this.graphiQlJsResource = graphiQlJsResource;
		this.graphiQlConfig = graphiQlConfig;
	}

	/**
	 * Handle the request, serving the GraphiQL page as HTML or adding a "path"
	 * param and redirecting back to the same URL if needed.
	 */
	public ServerResponse handleRequest(ServerRequest request) {
		if (request.path().endsWith("main.js")) {
			return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).body(graphiQlJsResource);
		}
		else if (request.path().endsWith("config.js")) {
			return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).body(buildConfigJs());
		}
		else if (request.path().endsWith("explorer")) {
			return ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(this.graphiQlHtmlResource);
		}
		else {
			URI url = request.uriBuilder().pathSegment("explorer").build();
			return ServerResponse.temporaryRedirect(url).build();
		}
	}

	private String buildConfigJs() {
		StringBuilder sb = new StringBuilder();
		this.graphiQlConfig.entrySet().forEach(entry -> {
			sb.append(String.format("window.GRAPHIGL_%s=\"%s\";", entry.getKey(), entry.getValue()));
		});
		return sb.toString();
	}
}
