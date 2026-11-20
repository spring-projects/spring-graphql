/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
