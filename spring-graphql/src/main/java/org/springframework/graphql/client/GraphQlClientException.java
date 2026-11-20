/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;


import org.jspecify.annotations.Nullable;

import org.springframework.core.NestedRuntimeException;
import org.springframework.graphql.GraphQlRequest;


/**
 * Base class for exceptions from {@code GraphQlClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class GraphQlClientException extends NestedRuntimeException {

	private final GraphQlRequest request;


	/**
	 * Constructor with a message, optional cause, and the request details.
	 * @param message the exception message to use
	 * @param cause the original cause for the client exception
	 * @param request the request that failed
	 */
	public GraphQlClientException(String message, @Nullable Throwable cause, GraphQlRequest request) {
		super(message, cause);
		this.request = request;
	}


	/**
	 * Return the request for which the error occurred.
	 */
	public GraphQlRequest getRequest() {
		return this.request;
	}

}
