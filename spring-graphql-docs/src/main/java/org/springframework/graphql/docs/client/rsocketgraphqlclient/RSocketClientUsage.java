/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.rsocketgraphqlclient;

import java.net.URI;

import io.rsocket.transport.netty.client.WebsocketClientTransport;

import org.springframework.graphql.client.RSocketGraphQlClient;

public class RSocketClientUsage {

	void create() {
		// tag::create[]
		URI uri = URI.create("wss://localhost:8080/rsocket");
		WebsocketClientTransport transport = WebsocketClientTransport.create(uri);

		RSocketGraphQlClient client = RSocketGraphQlClient.builder()
				.clientTransport(transport)
				.build();
		// end::create[]
	}
}
