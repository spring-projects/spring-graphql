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
import java.util.function.Consumer;

import graphql.ExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.client.WebSocketClient;

/**
 * Default {@link WebSocketGraphQlTester} that builds and uses a
 * {@link WebSocketGraphQlClient} for request execution.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultWebSocketGraphQlTester extends AbstractDelegatingGraphQlTester implements WebSocketGraphQlTester {

	private final WebSocketGraphQlClient webSocketGraphQlClient;

	private final Consumer<GraphQlTester.Builder<?>> builderInitializer;


	DefaultWebSocketGraphQlTester(
			GraphQlTester graphQlTester, WebSocketGraphQlClient webSocketGraphQlClient,
			Consumer<GraphQlTester.Builder<?>> builderInitializer) {

		super(graphQlTester);
		this.webSocketGraphQlClient = webSocketGraphQlClient;
		this.builderInitializer = builderInitializer;
	}


	@Override
	public Mono<Void> start() {
		return this.webSocketGraphQlClient.start();
	}

	@Override
	public Mono<Void> stop() {
		return this.webSocketGraphQlClient.stop();
	}

	@Override
	public Builder mutate() {
		Builder builder = new Builder(this.webSocketGraphQlClient);
		this.builderInitializer.accept(builder);
		return builder;
	}


	/**
	 * Default {@link WebSocketGraphQlTester.Builder} implementation.
	 */
	static final class Builder extends AbstractGraphQlTesterBuilder<Builder> implements WebSocketGraphQlTester.Builder<Builder> {

		private final WebSocketGraphQlClient.Builder<?> graphQlClientBuilder;

		/**
		 * Constructor to start via {@link WebSocketGraphQlTester#builder(URI, WebSocketClient)}.
		 */
		Builder(URI url, WebSocketClient webSocketClient) {
			Assert.notNull(webSocketClient, "WebSocketClient is required");
			this.graphQlClientBuilder = WebSocketGraphQlClient.builder(url, webSocketClient);
		}

		/**
		 * Constructor to mutate.
		 * @param client the underlying client with the current state
		 */
		Builder(WebSocketGraphQlClient client) {
			Assert.notNull(client, "WebSocketGraphQlClient is required");
			this.graphQlClientBuilder = client.mutate();
		}


		@Override
		public Builder url(String url) {
			this.graphQlClientBuilder.url(url);
			return this;
		}

		@Override
		public Builder url(URI url) {
			this.graphQlClientBuilder.url(url);
			return this;
		}

		@Override
		public Builder header(String name, String... values) {
			this.graphQlClientBuilder.header(name, values);
			return this;
		}

		@Override
		public Builder headers(Consumer<HttpHeaders> headersConsumer) {
			this.graphQlClientBuilder.headers(headersConsumer);
			return this;
		}

		@Override
		public Builder codecConfigurer(Consumer<CodecConfigurer> codecsConsumer) {
			this.graphQlClientBuilder.codecConfigurer(codecsConsumer);
			return this;
		}

		@Override
		public WebSocketGraphQlTester build() {
			WebSocketGraphQlClient client = this.graphQlClientBuilder.build();
			GraphQlTester graphQlTester = super.buildGraphQlTester(asTransport(client));
			return new DefaultWebSocketGraphQlTester(graphQlTester, client, getBuilderInitializer());
		}

		/**
		 * GraphQlTransport implementations are private, but we can create the
		 * GraphQlClient for it and adapt it.
		 */
		private static GraphQlTransport asTransport(GraphQlClient client) {
			return new GraphQlTransport() {

				@Override
				public Mono<ExecutionResult> execute(GraphQlRequest request) {
					return client
							.document(request.getDocument())
							.operationName(request.getOperationName())
							.variables(request.getVariables())
							.execute()
							.map(GraphQlClient.Response::andReturn);
				}

				@Override
				public Flux<ExecutionResult> executeSubscription(GraphQlRequest request) {
					return client
							.document(request.getDocument())
							.operationName(request.getOperationName())
							.variables(request.getVariables())
							.executeSubscription().map(GraphQlClient.Response::andReturn);
				}
			};
		}

	}

}
