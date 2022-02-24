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

import graphql.GraphQLError;

/**
 * Exception that is sent as an error signal to a {@code Flux} returned from
 * {@link GraphQlClient} or from its underlying {@link GraphQlTransport} for a
 * GraphQL over WebSocket subscription that ends with an "error" message.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class SubscriptionErrorException extends RuntimeException {

	private final List<GraphQLError> errors;


	public SubscriptionErrorException(List<GraphQLError> errors) {
		super("GraphQL subscription error: " + errors);
		this.errors = errors;
	}


	/**
	 * Return the errors contained in the GraphQL over WebSocket "errors" message.
	 */
	public List<GraphQLError> getErrors() {
		return this.errors;
	}

}
