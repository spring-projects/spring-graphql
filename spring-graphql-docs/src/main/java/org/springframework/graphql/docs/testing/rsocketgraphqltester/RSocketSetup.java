/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.rsocketgraphqltester;

import java.net.URI;

import io.rsocket.transport.netty.client.WebsocketClientTransport;

import org.springframework.graphql.test.tester.RSocketGraphQlTester;

public class RSocketSetup {

	void rSocketSetup() {
		// tag::rsocketSetup[]
		URI url = URI.create("wss://localhost:8080/rsocket");
		WebsocketClientTransport transport = WebsocketClientTransport.create(url);

		RSocketGraphQlTester client = RSocketGraphQlTester.builder()
				.clientTransport(transport)
				.build();
		// end::rsocketSetup[]
	}
}
