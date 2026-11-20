/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import java.util.Map;

import org.springframework.graphql.GraphQlRequest;


/**
 * {@link GraphQlRequest} for client side use.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ClientGraphQlRequest extends GraphQlRequest {

	/**
	 * Return the client request attributes.
	 * <p>The attributes purely for client side request processing, i.e. available
	 * throughout the {@link GraphQlClientInterceptor} chain, but not sent.
	 */
	Map<String, Object> getAttributes();

}

