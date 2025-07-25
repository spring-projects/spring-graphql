/*
 * Copyright 2002-present the original author or authors.
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


import org.jspecify.annotations.Nullable;

import org.springframework.graphql.GraphQlRequest;

/**
 * Exception raised by a {@link GraphQlTransport} or used to wrap an exception
 * from a {@code GraphQlTransport} implementation.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class GraphQlTransportException extends GraphQlClientException {

	/**
	 * Constructor with a default message.
	 * @param cause the original cause of the transport error
	 * @param request the request that failed at the transport level
	 */
	public GraphQlTransportException(@Nullable Throwable cause, GraphQlRequest request) {
		super("GraphQlTransport error" + ((cause != null) ? ": " + cause.getMessage() : ""), cause, request);
	}

	/**
	 * Constructor with a given message.
	 * @param message the exception message to use
	 * @param cause the original cause of the transport error
	 * @param request the request that failed at the transport level
	 */
	public GraphQlTransportException(String message, @Nullable Throwable cause, GraphQlRequest request) {
		super(message, cause, request);
	}

}
