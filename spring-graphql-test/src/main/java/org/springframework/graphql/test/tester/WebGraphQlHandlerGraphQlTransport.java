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

package org.springframework.graphql.test.tester;


import java.net.URI;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.lang.Nullable;


/**
 * {@code GraphQlTransport} that calls directly a {@link WebGraphQlHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class WebGraphQlHandlerGraphQlTransport extends AbstractDirectGraphQlTransport {

	private final URI url;

	private final HttpHeaders headers = new HttpHeaders();

	private final WebGraphQlHandler graphQlHandler;

	private final CodecConfigurer codecConfigurer;


	WebGraphQlHandlerGraphQlTransport(
			@Nullable URI url, HttpHeaders headers, WebGraphQlHandler handler, CodecConfigurer configurer) {

		this.url = (url != null ? url : URI.create(""));
		this.headers.addAll(headers);
		this.graphQlHandler = handler;
		this.codecConfigurer = configurer;
	}


	public URI getUrl() {
		return this.url;
	}

	public HttpHeaders getHeaders() {
		return this.headers;
	}

	public WebGraphQlHandler getGraphQlHandler() {
		return this.graphQlHandler;
	}

	public CodecConfigurer getCodecConfigurer() {
		return this.codecConfigurer;
	}


	@Override
	protected Mono<ExecutionGraphQlResponse> executeInternal(ExecutionGraphQlRequest executionRequest) {

		String id = idGenerator.generateId().toString();
		Map<String, Object> body = executionRequest.toMap();

		WebGraphQlRequest webExecutionRequest =
				new WebGraphQlRequest(this.url, this.headers, body, id, null);

		return this.graphQlHandler.handleRequest(webExecutionRequest).cast(ExecutionGraphQlResponse.class);
	}

}
