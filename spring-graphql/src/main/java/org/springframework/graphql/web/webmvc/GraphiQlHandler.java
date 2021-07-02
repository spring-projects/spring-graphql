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

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Spring MVC functional handler that renders a GraphiQl UI page.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 */
public class GraphiQlHandler {

	private final String graphQlPath;

	private final Resource graphiQlResource;


	/**
	 * Create an instance.
	 * @param graphQlPath the path to the GraphQL endpoint
	 * @param graphiQlResource the GraphiQL page
	 */
	public GraphiQlHandler(String graphQlPath, Resource graphiQlResource) {
		this.graphQlPath = graphQlPath;
		this.graphiQlResource = graphiQlResource;
	}


	/**
	 * Handle the request, serving the GraphiQL page as HTML or adding a "path"
	 * param and redirecting back to the same URL if needed.
	 */
	public ServerResponse handleRequest(ServerRequest request) {
		if (!request.param("path").isPresent()) {
			URI url = request.uriBuilder().queryParam("path", this.graphQlPath).build();
			return ServerResponse.temporaryRedirect(url).build();
		}
		return ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(this.graphiQlResource);
	}

}
