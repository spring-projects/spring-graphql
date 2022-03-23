/*
 * Copyright 2020-2022 the original author or authors.
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


import java.util.Map;

import reactor.core.publisher.Mono;


/**
 * An extension of {@link GraphQlClientInterceptor} with additional methods to
 * for WebSocket interception points. Only a single interceptor of type
 * {@link WebSocketGraphQlClientInterceptor} may be configured.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebSocketGraphQlClientInterceptor extends GraphQlClientInterceptor {

	/**
	 * Provide a {@code Mono} that returns the payload for the
	 * {@code "connection_init"} message. The {@code Mono} is subscribed to every
	 * type a new WebSocket connection is established.
	 */
	default Mono<Object> connectionInitPayload() {
		return Mono.empty();
	}

	/**
	 * Handler the {@code "connection_ack"} message received from the server at
	 * the start of the WebSocket connection.
	 * @param ackPayload the payload of the {@code "connection_ack"} message
	 */
	default Mono<Void> handleConnectionAck(Map<String, Object> ackPayload) {
		return Mono.empty();
	}

}
