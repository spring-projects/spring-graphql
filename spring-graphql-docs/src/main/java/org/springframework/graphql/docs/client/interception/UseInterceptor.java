/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.interception;

import java.net.URI;

import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

public class UseInterceptor {

	void registerInterceptor() {
		// tag::register[]
		URI url = URI.create("wss://localhost:8080/graphql");
		WebSocketClient client = new ReactorNettyWebSocketClient();

		WebSocketGraphQlClient graphQlClient = WebSocketGraphQlClient.builder(url, client)
				.interceptor(new MyInterceptor())
				.build();
		// end::register[]
	}
}
