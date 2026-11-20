/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.server.transports.rsocket;

import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.GraphQlRSocketHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GraphQlRSocketController {

	private final GraphQlRSocketHandler handler;

	GraphQlRSocketController(GraphQlRSocketHandler handler) {
		this.handler = handler;
	}

	@MessageMapping("graphql")
	public Mono<Map<String, Object>> handle(Map<String, Object> payload) {
		return this.handler.handle(payload);
	}

	@MessageMapping("graphql")
	public Flux<Map<String, Object>> handleSubscription(Map<String, Object> payload) {
		return this.handler.handleSubscription(payload);
	}
}
