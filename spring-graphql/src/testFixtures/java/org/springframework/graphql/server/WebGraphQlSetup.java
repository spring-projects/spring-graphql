/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import org.springframework.graphql.server.webflux.GraphQlHttpHandler;

/**
 * Workflow that results in the creation of a {@link WebGraphQlHandler} or
 * an HTTP handler for WebMvc or WebFlux.
 *
 * @author Rossen Stoyanchev
 */
public interface WebGraphQlSetup {

	WebGraphQlSetup interceptor(WebGraphQlInterceptor... interceptors);

	WebGraphQlHandler toWebGraphQlHandler();

	org.springframework.graphql.server.webmvc.GraphQlHttpHandler toHttpHandler();

	GraphQlHttpHandler toHttpHandlerWebFlux();

}
