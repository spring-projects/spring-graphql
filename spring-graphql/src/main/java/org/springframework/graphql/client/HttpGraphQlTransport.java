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
package org.springframework.graphql.client;

import java.util.Map;

import graphql.ExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Transport to execute GraphQL requests over HTTP via {@link WebClient}.
 * Supports only single-response requests over HTTP POST. For subscription
 * requests, see {@link WebSocketGraphQlTransport}.
 *
 * <p>Use the builder to initialize the transport and the {@code GraphQlClient}
 * in a single chain:
 *
 * <pre style="class">
 * GraphQlClient client = HttpGraphQlTransport.builder(webClient).buildClient();
 * </pre>
 *
 * <p>Or build the transport and the client separately:
 *
 * <pre style="class">
 * HttpGraphQlTransport transport = HttpGraphQlTransport.create(webClient);
 * GraphQlClient client = GraphQlClient.create(transport);
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class HttpGraphQlTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final WebClient webClient;


	private HttpGraphQlTransport(WebClient webClient) {
		Assert.notNull(webClient, "WebClient is required");
		this.webClient = webClient;
	}


	/**
	 * Return the underlying {@code WebClient}.
	 */
	public WebClient getWebClient() {
		return this.webClient;
	}


	@Override
	public Mono<ExecutionResult> execute(GraphQlRequest request) {
		return this.webClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(request.toMap())
				.retrieve()
				.bodyToMono(MAP_TYPE)
				.map(MapExecutionResult::new);
	}

	@Override
	public Flux<ExecutionResult> executeSubscription(GraphQlRequest request) {
		throw new UnsupportedOperationException("Subscriptions not supported over HTTP");
	}


	/**
	 * Static factory method with a {@code WebClient} to use.
	 */
	public static HttpGraphQlTransport create(WebClient webClient) {
		return new HttpGraphQlTransport(webClient);
	}

	/**
	 * Static method to obtain a {@code Builder}.
	 */
	public static Builder builder(WebClient webClient) {
		return new Builder(webClient);
	}


	/**
	 * Builder for {@link HttpGraphQlTransport} or a {@link GraphQlClient}
	 * configured with the transport.
	 */
	public static class Builder {

		private WebClient webClient;

		private Builder(WebClient webClient) {
			this.webClient = webClient;
		}

		/**
		 * Set the {@code WebClient} to use.
		 */
		public Builder webClient(WebClient webClient) {
			this.webClient = webClient;
			return this;
		}

		/**
		 * Build the {@code HttpGraphQlTransport} instance.
		 */
		public HttpGraphQlTransport build() {
			return new HttpGraphQlTransport(this.webClient);
		}

		/**
		 * Continue on to build a {@link GraphQlClient} configured with the
		 * transport configured here so far.
		 */
		public GraphQlClient.Builder configureClient() {
			return GraphQlClient.builder(build());
		}

		/**
		 * Shortcut to build a {@link GraphQlClient} configured with the
		 * transport configured here.
		 */
		public GraphQlClient buildClient() {
			return GraphQlClient.builder(build()).build();
		}

	}


}
