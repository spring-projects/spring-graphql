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
