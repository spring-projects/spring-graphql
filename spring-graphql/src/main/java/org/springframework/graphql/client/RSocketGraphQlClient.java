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

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import io.rsocket.core.RSocketClient;
import io.rsocket.loadbalance.LoadbalanceStrategy;
import io.rsocket.loadbalance.LoadbalanceTarget;
import io.rsocket.transport.ClientTransport;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.MimeType;


/**
 * GraphQL over RSocket client that uses {@link RSocketRequester}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface RSocketGraphQlClient extends GraphQlClient {

	/**
	 * Start the RSocket session.
	 * @return {@code Mono} that completes when the underlying session is started
	 */
	Mono<Void> start();

	/**
	 * Stop the RSocket session.
	 * @return {@code Mono} that completes when the underlying session is stopped.
	 * <p>Note that currently this method calls {@link RSocketClient#dispose()}
	 * which is not differed and does not wait, i.e. it triggers stopping
	 * immediately and returns immediately.
	 * See <a href="https://github.com/rsocket/rsocket-java/issues/1048">rsocket-java#1048</a>
	 */
	Mono<Void> stop();

	@Override
	Builder<?> mutate();


	/**
	 * Start with a new {@link RSocketRequester.Builder} customized for GraphQL,
	 * setting the {@code dataMimeType} to {@code "application/graphql+json"}
	 * and adding JSON codecs.
	 */
	static Builder<?> builder() {
		return new DefaultRSocketGraphQlClientBuilder();
	}

	/**
	 * Start with a given {@link #builder()}.
	 */
	static Builder<?> builder(RSocketRequester.Builder requesterBuilder) {
		return new DefaultRSocketGraphQlClientBuilder(requesterBuilder);
	}


	/**
	 * Builder for the GraphQL over HTTP client.
	 */
	interface Builder<B extends Builder<B>> extends GraphQlClient.Builder<B> {

		/**
		 * Select TCP as the underlying network protocol. This delegates to
		 * {@link RSocketRequester.Builder#tcp(String, int)}  to create the
		 * {@code RSocketRequester} instance.
		 * @param host the remote host to connect to
		 * @param port the remote port to connect to
		 * @return the same builder instance
		 */
		B tcp(String host, int port);

		/**
		 * Select WebSocket as the underlying network protocol. This delegates to
		 * {@link RSocketRequester.Builder#websocket(URI)} to create the
		 * {@code RSocketRequester} instance.
		 * @param uri the URL for the WebSocket handshake
		 * @return the same builder instance
		 */
		B webSocket(URI uri);

		/**
		 * Use a given {@link ClientTransport} to communicate with the remote
		 * server. This delegates to
		 * {@link RSocketRequester.Builder#transport(ClientTransport)} to create
		 * the {@code RSocketRequester} instance.
		 * @param clientTransport the transport to use
		 * @return the same builder instance
		 */
		B clientTransport(ClientTransport clientTransport);

		/**
		 * Use a {@link Publisher} of {@link LoadbalanceTarget}s, each of which
		 * contains a {@link ClientTransport}. This delegates to
		 * {@link RSocketRequester.Builder#transports(Publisher, LoadbalanceStrategy)}
		 * to create the {@code RSocketRequester} instance.
		 * @param targetPublisher supplies list of targets to loadbalance against;
		 * the targets are replaced when the given {@code Publisher} emits again.
		 * @param loadbalanceStrategy the strategy to use for selecting from
		 * the list of targets.
		 * @return the same builder instance
		 * @since 1.0.3
		 */
		B clientTransports(Publisher<List<LoadbalanceTarget>> targetPublisher, LoadbalanceStrategy loadbalanceStrategy);

		/**
		 * Customize the format of data payloads for the connection.
		 * <p>By default, this is set to {@code "application/graphql+json"} but
		 * it can be changed to {@code "application/json"} if necessary.
		 * @param dataMimeType the mime type to use
		 * @return the same builder instance
		 */
		B dataMimeType(MimeType dataMimeType);

		/**
		 * Customize the route to specify in the metadata of each request so the
		 * server can route it to the handler for GraphQL requests.
		 * @param route the route
		 * @return the same builder instance
		 */
		B route(String route);

		/**
		 * Customize the underlying {@code RSocketRequester} to use.
		 * <p>Note that some properties of {@code RSocketRequester.Builder} like the
		 * data MimeType, and the underlying RSocket transport can be customized
		 * through this builder.
		 * @see #dataMimeType(MimeType)
		 * @see #tcp(String, int)
		 * @see #webSocket(URI)
		 * @see #clientTransport(ClientTransport)
		 * @return the same builder instance
		 */
		B rsocketRequester(Consumer<RSocketRequester.Builder> requester);

		/**
		 * Build the {@code RSocketGraphQlClient} instance.
		 */
		@Override
		RSocketGraphQlClient build();

	}

}
