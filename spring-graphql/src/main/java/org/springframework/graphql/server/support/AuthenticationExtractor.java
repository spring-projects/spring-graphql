/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.support;

import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.security.core.Authentication;

/**
 * Strategy to extract an {@link Authentication} from the payload of a
 * {@code "connection_init"} GraphQL over WebSocket message.
 *
 * @author Joshua Cummings
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public interface AuthenticationExtractor {

	/**
	 * Return the authentication contained in the given payload, or an empty {@code Mono}.
	 * @param payload the payload to extract the authentication value from
	 */
	Mono<Authentication> getAuthentication(Map<String, Object> payload);

}
