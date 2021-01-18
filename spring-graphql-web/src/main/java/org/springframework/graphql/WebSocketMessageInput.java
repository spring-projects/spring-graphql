/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpHeaders;

/**
 * Extension of {@link WebInput} that contains a GraphQL subscription received
 * as a message over a WebSocket connection.
 */
public class WebSocketMessageInput extends WebInput {

	private final String requestId;


	public WebSocketMessageInput(
			URI uri, HttpHeaders headers, String subscribeId, Map<String, Object> payload) {

		super(uri, headers, payload);
		this.requestId = subscribeId;
	}


	/**
	 * Return the id that will correlate server responses to client requests
	 * within a multiplexed WebSocket connection.
	 */
	public String requestId() {
		return this.requestId;
	}

	@Override
	public String toString() {
		return "requestId='" + requestId() + "', " + super.toString();
	}

}
