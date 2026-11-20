/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;


import java.net.URI;
import java.util.Collections;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;


/**
 * {@code GraphQlTransport} that calls directly a {@link WebGraphQlHandler}.
 *
 * @author Rossen Stoyanchev
 */
final class WebGraphQlHandlerGraphQlTransport extends AbstractDirectGraphQlTransport {

	private final URI url;

	private final HttpHeaders headers = new HttpHeaders();

	private final WebGraphQlHandler graphQlHandler;

	private final CodecConfigurer codecConfigurer;


	WebGraphQlHandlerGraphQlTransport(
			@Nullable URI url, HttpHeaders headers, WebGraphQlHandler handler, CodecConfigurer configurer) {

		this.url = (url != null) ? url : URI.create("");
		this.headers.addAll(headers);
		this.graphQlHandler = handler;
		this.codecConfigurer = configurer;
	}


	URI getUrl() {
		return this.url;
	}

	HttpHeaders getHeaders() {
		return this.headers;
	}

	WebGraphQlHandler getGraphQlHandler() {
		return this.graphQlHandler;
	}

	CodecConfigurer getCodecConfigurer() {
		return this.codecConfigurer;
	}


	@Override
	protected Mono<ExecutionGraphQlResponse> executeInternal(ExecutionGraphQlRequest executionRequest) {

		WebGraphQlRequest request = new WebGraphQlRequest(
				this.url, this.headers, null,  null, Collections.emptyMap(), executionRequest,
				idGenerator.generateId().toString(), null);

		return this.graphQlHandler.handleRequest(request).cast(ExecutionGraphQlResponse.class);
	}

}
