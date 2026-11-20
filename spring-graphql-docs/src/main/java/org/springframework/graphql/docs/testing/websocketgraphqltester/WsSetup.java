/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.websocketgraphqltester;

import java.net.URI;

import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

public class WsSetup {

	void setup() {
		// tag::setup[]
		String url = "http://localhost:8080/graphql";
		WebSocketClient client = new ReactorNettyWebSocketClient();

		WebSocketGraphQlTester tester = WebSocketGraphQlTester.builder(url, client).build();
		// end::setup[]
	}

	void customSetup() {
		// tag::customSetup[]
		URI url = URI.create("ws://localhost:8080/graphql");
		WebSocketClient client = new ReactorNettyWebSocketClient();

		WebSocketGraphQlTester tester = WebSocketGraphQlTester.builder(url, client)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();

		// Use tester...

		WebSocketGraphQlTester anotherTester = tester.mutate()
				.headers((headers) -> headers.setBasicAuth("peter", "..."))
				.build();

		// Use anotherTester...
		// end::customSetup[]
	}
}
