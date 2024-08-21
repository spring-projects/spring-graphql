/*
 * Copyright 2020-2024 the original author or authors.
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
