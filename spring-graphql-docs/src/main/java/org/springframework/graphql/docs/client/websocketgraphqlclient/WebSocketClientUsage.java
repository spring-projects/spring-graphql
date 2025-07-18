/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.docs.client.websocketgraphqlclient;

import java.time.Duration;

import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

public class WebSocketClientUsage {

	void create() {
		// tag::create[]
		String url = "wss://spring.io/graphql";
		WebSocketClient client = new ReactorNettyWebSocketClient();

		WebSocketGraphQlClient graphQlClient = WebSocketGraphQlClient.builder(url, client).build();
		// end::create[]
	}

	void mutate() {
		// tag::mutate[]
		String url = "wss://spring.io/graphql";
		WebSocketClient client = new ReactorNettyWebSocketClient();

		WebSocketGraphQlClient graphQlClient = WebSocketGraphQlClient.builder(url, client)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();

		// Use graphQlClient...

		WebSocketGraphQlClient anotherGraphQlClient = graphQlClient.mutate()
				.headers((headers) -> headers.setBasicAuth("peter", "..."))
				.build();

		// Use anotherGraphQlClient...

		// end::mutate[]
	}

	void keepAlive() {
		// tag::keepAlive[]
		String url = "wss://spring.io/graphql";
		WebSocketClient client = new ReactorNettyWebSocketClient();

		WebSocketGraphQlClient graphQlClient = WebSocketGraphQlClient.builder(url, client)
				.keepAlive(Duration.ofSeconds(30))
				.build();
		// end::keepAlive[]
	}

}
