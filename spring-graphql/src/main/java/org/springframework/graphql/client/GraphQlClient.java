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

import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.lang.Nullable;

/**
 * Define a workflow to execute GraphQL requests that is independent of the
 * underlying transport.
 *
 * <p>For most cases, use a transport specific extension:
 * <ul>
 * <li>{@link HttpGraphQlClient}
 * <li>{@link WebSocketGraphQlClient}
 * </ul>
 *
 * <p>Alternatively, create an instance with any other transport via
 * {@link #builder(GraphQlTransport)}. Or create a transport specific extension
 * similar to HTTP and WebSocket.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlClient {

	/**
	 * Start defining a GraphQL request with the given document, which is the
	 * textual representation of an operation (or operations) to perform,
	 * including selection sets and fragments.
	 * @param document the document for the request
	 * @return spec to further define or execute the request
	 */
	Request document(String document);

	/**
	 * Variant of {@link #document(String)} that uses the given key to resolve
	 * the GraphQL document from a file, or in another way with the help of the
	 * {@link DocumentSource} that the client is configured with.
	 * @throws IllegalArgumentException if the content could not be loaded
	 */
	Request documentName(String name);

	/**
	 * Return a builder initialized from the configuration of "this" client
	 * to use to build a new, independently configured client instance.
	 */
	GraphQlClient.Builder<?> mutate();


	/**
	 * Create a builder with the given custom {@code GraphQlTransport}.
	 * <p>For most cases, use a transport specific extension such as
	 * {@link HttpGraphQlClient} or {@link WebSocketGraphQlClient}. This method
	 * is for use with a custom {@code GraphQlTransport}.
	 * @param transport the transport to execute requests with
	 * @return the builder for further initialization
	 */
	static Builder<?> builder(GraphQlTransport transport) {
		return new DefaultGraphQlClient.Builder(transport);
	}


	/**
	 * Defines a builder for creating {@link GraphQlClient} instances.
	 */
	interface Builder<B extends Builder<B>> {

		/**
		 * Configure a {@link DocumentSource} for use with
		 * {@link #documentName(String)} for resolving a document by name.
		 * <p>By default, {@link ResourceDocumentSource} is used.
		 */
		B documentSource(DocumentSource contentLoader);

		/**
		 * Build the {@code GraphQlClient} instance.
		 */
		GraphQlClient build();

	}


	/**
	 * Declare options to gather input for a GraphQL request and execute it.
	 */
	interface Request {

		/**
		 * Set the name of the operation in the {@link #document(String) document}
		 * to execute, if the document contains multiple operations.
		 * @param operationName the operation name
		 * @return this request spec
		 */
		Request operationName(@Nullable String operationName);

		/**
		 * Add a value for a variable defined by the operation.
		 * @param name the variable name
		 * @param value the variable value
		 * @return this request spec
		 */
		Request variable(String name, Object value);

		/**
		 * Add all given values for variables defined by the operation.
		 * @param variables the variable values
		 * @return this request spec
		 */
		Request variables(Map<String, Object> variables);

		/**
		 * Execute as a request with a single response such as a "query" or
		 * "mutation" operation.
		 * @return a {@code Mono} with a {@code ClientGraphQlResponse} for further
		 * decoding of the response. The {@code Mono} may end wth an error due
		 * to transport level issues.
		 */
		Mono<ClientGraphQlResponse> execute();

		/**
		 * Execute a "subscription" request with a stream of responses.
		 * @return a {@code Flux} with a {@code ClientGraphQlResponse} for further
		 * decoding of the response. The {@code Flux} may terminate as follows:
		 * <ul>
		 * <li>Completes if the subscription completes before the connection is closed.
		 * <li>{@link SubscriptionErrorException} if the subscription ends with an error.
		 * <li>{@link IllegalStateException} if the connection is closed or lost
		 * before the stream terminates.
		 * <li>Exception for connection and GraphQL session initialization issues.
		 * </ul>
		 * <p>The {@code Flux} may be cancelled to notify the server to end the
		 * subscription stream.
		 */
		Flux<ClientGraphQlResponse> executeSubscription();

	}

}