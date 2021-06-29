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

package org.springframework.graphql.boot;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Servlet.fn handler for the GraphiQl UI.
 * @author Brian Clozel
 */
class WebMvcGraphiQlHandler {

	private final String graphQlPath;

	private final Resource graphiQlResource;

	public WebMvcGraphiQlHandler(String graphQlPath, Resource graphiQlResource) {
		this.graphQlPath = graphQlPath;
		this.graphiQlResource = graphiQlResource;
	}

	public ServerResponse showGraphiQlPage(ServerRequest request) {
		if (request.param("path").isPresent()) {
			return ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(this.graphiQlResource);
		}
		else {
			return ServerResponse.temporaryRedirect(request.uriBuilder().queryParam("path", this.graphQlPath).build()).build();
		}
	}
}
