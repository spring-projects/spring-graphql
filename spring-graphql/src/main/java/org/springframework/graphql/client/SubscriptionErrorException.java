/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.List;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.ResponseError;

/**
 * WebSocket {@link GraphQlTransportException} raised when a subscription
 * ends with an {@code "error"} message. The {@link #getErrors()} method provides
 * access to the GraphQL errors from the message payload.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class SubscriptionErrorException extends GraphQlTransportException {

	private final List<ResponseError> errors;


	/**
	 * Constructor with the request details and the errors listed in the payload
	 * of the {@code "errors"} message.
	 */
	public SubscriptionErrorException(GraphQlRequest request, List<ResponseError> errors) {
		super("GraphQL subscription completed with an \"error\" message, " +
				"with the following errors: " + errors, null, request);
		this.errors = errors;
	}


	/**
	 * Return the errors contained in the GraphQL over WebSocket "errors" message.
	 */
	public List<ResponseError> getErrors() {
		return this.errors;
	}

}
