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

import java.time.Duration;
import java.util.List;

import io.rsocket.Closeable;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.exceptions.RejectedException;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RSocketGraphQlTransport} connecting to a
 * {@link LocalServerTransport} and receiving stubbed responses via {@link SocketAcceptor}.
 *
 * @author Rossen Stoyanchev
 */
public class RSocketGraphQlTransportTests {

	private static final Jackson2JsonEncoder jsonEncoder = new Jackson2JsonEncoder();

	private static final Jackson2JsonDecoder jsonDecoder = new Jackson2JsonDecoder();


	@Nullable
	private Closeable server;


	@AfterEach
	void tearDown() {
		if (this.server != null) {
			this.server.dispose();
		}
	}


	@Test
	void subscriptionError() {

		RSocketGraphQlTransport transport = createTransport(SocketAcceptor.forRequestStream(payload ->
				Flux.error(new RejectedException(
						"[{\"message\":\"boo\"," +
								"\"locations\":[]," +
								"\"errorType\":\"DataFetchingException\"," +
								"\"path\":null," +
								"\"extensions\":null}]"))));

		Flux<GraphQlResponse> responseFlux =
				transport.executeSubscription(new DefaultGraphQlRequest("subscription { greetings }"));

		StepVerifier.create(responseFlux)
				.expectErrorSatisfies(ex -> {
					assertThat(ex).isInstanceOf(SubscriptionErrorException.class);
					List<ResponseError> errors = ((SubscriptionErrorException) ex).getErrors();
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getMessage()).isEqualTo("boo");
				})
				.verify(Duration.ofSeconds(5));
	}

	private RSocketGraphQlTransport createTransport(SocketAcceptor acceptor) {

		this.server = RSocketServer.create()
				.acceptor(acceptor)
				.bind(LocalServerTransport.create("local"))
				.block();

		RSocketRequester requester = RSocketRequester.builder()
				.rsocketStrategies(RSocketStrategies.builder().encoder(jsonEncoder).decoder(jsonDecoder).build())
				.transport(LocalClientTransport.create("local"));

		return new RSocketGraphQlTransport("route", requester, jsonDecoder);
	}

}
