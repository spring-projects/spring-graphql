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
import java.time.Duration;
import java.util.Collections;

import graphql.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.support.MapExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GraphQlClient.Builder}.
 * @author Rossen Stoyanchev
 */
public class WebSocketGraphQlClientBuilderTests {

	private TestWebSocketClient webSocketClient;


	@BeforeEach
	void setUp() {
		MockGraphQlWebSocketServer mockServer = new MockGraphQlWebSocketServer();
		this.webSocketClient = new TestWebSocketClient(mockServer);

		ExecutionResult result = MapExecutionResult.forDataOnly(Collections.singletonMap("key1", "value1"));
		mockServer.expectOperation("{Query1}").andRespond(result);
	}


	@Test
	void mutate() {

		// Original

		URI url = URI.create("/graphql");

		WebSocketGraphQlClient graphQlClient = WebSocketGraphQlClient.builder(this.webSocketClient)
				.url(url)
				.header("header1", "value1")
				.header("header2", "value2")
				.build();

		graphQlClient.document("{Query1}").execute().block(Duration.ofSeconds(5));

		assertThat(this.webSocketClient.getConnectionCount()).isEqualTo(1);
		assertThat(this.webSocketClient.getConnection(0).getUrl()).isEqualTo(url);
		assertThat(this.webSocketClient.getConnection(0).getHeaders()).hasSize(2)
				.containsEntry("header1", Collections.singletonList("value1"))
				.containsEntry("header2", Collections.singletonList("value2"));

		// Mutate

		URI anotherUrl = URI.create("/another-graphql");

		WebSocketGraphQlClient anotherClient = graphQlClient.mutate()
				.url(anotherUrl)
				.headers(headers -> {
					headers.set("header1", "anotherValue1");
					headers.set("header2", "anotherValue2");
				})
				.build();

		anotherClient.document("{Query1}").execute().block(Duration.ofSeconds(5));

		assertThat(this.webSocketClient.getConnectionCount()).isEqualTo(2);
		assertThat(this.webSocketClient.getConnection(1).getUrl()).isEqualTo(anotherUrl);
		assertThat(this.webSocketClient.getConnection(1).getHeaders()).hasSize(2)
				.containsEntry("header1", Collections.singletonList("anotherValue1"))
				.containsEntry("header2", Collections.singletonList("anotherValue2"));

		// Original not affected (stop + start original client, to connect again)

		graphQlClient.stop().block(Duration.ofSeconds(5));
		graphQlClient.start().block(Duration.ofSeconds(5));
		graphQlClient.document("{Query1}").execute().block();

		assertThat(this.webSocketClient.getConnection(0).getUrl()).isEqualTo(url);
		assertThat(this.webSocketClient.getConnection(0).getHeaders()).hasSize(2)
				.containsEntry("header1", Collections.singletonList("value1"))
				.containsEntry("header2", Collections.singletonList("value2"));
	}

}
