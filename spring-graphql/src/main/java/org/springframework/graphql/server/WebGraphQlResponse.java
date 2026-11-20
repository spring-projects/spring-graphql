/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import java.util.function.Consumer;

import graphql.ExecutionResult;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;


/**
 * {@link org.springframework.graphql.GraphQlResponse} implementation for server
 * handling over HTTP or over WebSocket.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebGraphQlResponse extends DefaultExecutionGraphQlResponse {

	private final HttpHeaders responseHeaders;


	/**
	 * Create an instance that wraps the given {@link ExecutionGraphQlResponse}.
	 * @param response the response to wrap
	 */
	public WebGraphQlResponse(ExecutionGraphQlResponse response) {
		super(response);
		this.responseHeaders = new HttpHeaders();
	}

	private WebGraphQlResponse(WebGraphQlResponse original, ExecutionResult executionResult) {
		super(original.getExecutionInput(), executionResult);
		this.responseHeaders = original.getResponseHeaders();
	}


	/**
	 * Return the headers to be added to the HTTP response.
	 * <p>By default, this is empty.
	 * <p><strong>Note:</strong> This is for use with GraphQL over HTTP requests
	 * but not for GraphQL over WebSocket where the initial handshake HTTP
	 * request completes before queries begin.
	 */
	public HttpHeaders getResponseHeaders() {
		return this.responseHeaders;
	}

	/**
	 * Transform the underlying {@link ExecutionResult} through a {@link Builder}
	 * and return a new instance with the modified values.
	 * @param consumer callback to transform the result
	 * @return the new response instance with the mutated {@code ExecutionResult}
	 */
	public WebGraphQlResponse transform(Consumer<Builder> consumer) {
		Builder builder = new Builder(this);
		consumer.accept(builder);
		return builder.build();
	}


	/**
	 * Builder to transform a {@link WebGraphQlResponse}.
	 */
	public static final class Builder extends DefaultExecutionGraphQlResponse.Builder<Builder, WebGraphQlResponse> {

		private Builder(WebGraphQlResponse original) {
			super(original);
		}

		@Override
		protected WebGraphQlResponse build(WebGraphQlResponse original, ExecutionResult newResult) {
			return new WebGraphQlResponse(original, newResult);
		}

	}

}
