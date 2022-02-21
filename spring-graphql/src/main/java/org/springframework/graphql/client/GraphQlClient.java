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

import com.jayway.jsonpath.Configuration;
import graphql.GraphQLError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;

/**
 * Defines a workflow to prepare and execute GraphQL requests and to decode and
 * handle responses.
 *
 * <p>To create a {@link GraphQlClient}, use the builder in this class, and see
 * examples in {@link HttpGraphQlTransport} and {@link WebSocketGraphQlTransport}
 * for initializing both the client and the transport it runs over.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see HttpGraphQlTransport
 * @see WebSocketGraphQlTransport
 */
public interface GraphQlClient {

	/**
	 * Return the underlying transport or {@code null} if the required type
	 * does not match the transport type. See {@link GraphQlTransport}
	 */
	@Nullable
	<T extends GraphQlTransport> T getTransport(Class<T> requiredType);

	/**
	 * Start a new request with the given GraphQL operation, which can be a
	 * query, a mutation, or subscription.
	 * @param operation the operation to perform
	 * @return spec to further define and execute the request
	 */
	RequestSpec operation(String operation);

	/**
	 * Variant of {@link #operation(String)} that loads the content of the
	 * operation from the given key through the configured
	 * {@link OperationContentLoader}.
	 * @throws IllegalArgumentException if the content could not be loaded
	 */
	RequestSpec loadOperationContent(String key);


	/**
	 * Return a builder to initialize a {@link GraphQlClient} instance.
	 * @param transport the transport for executing requests over
	 * @see HttpGraphQlTransport
	 * @see WebSocketGraphQlTransport
	 */
	static Builder builder(GraphQlTransport transport) {
		return new DefaultGraphQlClientBuilder(transport);
	}


	/**
	 * Defines a builder for creating {@link GraphQlClient} instances.
	 */
	interface Builder {

		/**
		 * Provide JSONPath configuration settings.
		 * <p>By default, the Jackson JSON library is used if present.
		 */
		Builder jsonPathConfig(@Nullable Configuration config);

		/**
		 * Configure an {@link OperationContentLoader} to use with
		 * {@link #loadOperationContent(String) GraphQlClient#loadOperationContent}.
		 * <p>By default, {@link ResourceOperationContentLoader} is used.
		 */
		Builder operationContentLoader(@Nullable OperationContentLoader contentLoader);

		/**
		 * Build the {@code GraphQlClient} instance.
		 */
		GraphQlClient build();

	}


	/**
	 * Declare options to execute a request.
	 */
	interface ExecuteSpec {

		/**
		 * Execute as a single-response operation such as "query" or "mutation".
		 * @return a {@code Mono} with a {@code ResponseSpec} for further
		 * decoding of the response. The {@code Mono} may end wth an error due
		 * to transport level issues.
		 */
		Mono<ResponseSpec> execute();

		/**
		 * Execute a "subscription" request and stream a stream of responses.
		 * @return a {@code Flux} with a {@code ResponseSpec} for further
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
		Flux<ResponseSpec> executeSubscription();

	}


	/**
	 * Declare options to gather input for a GraphQL request and execute it.
	 */
	interface RequestSpec extends ExecuteSpec {

		/**
		 * Set the operation name.
		 * @param name the operation name
		 * @return this request spec
		 */
		RequestSpec operationName(@Nullable String name);

		/**
		 * Add a variable.
		 * @param name the variable name
		 * @param value the variable value
		 * @return this request spec
		 */
		RequestSpec variable(String name, Object value);

	}


	/**
	 * Declare options to decode a response.
	 */
	interface ResponseSpec {

		/**
		 * Switch to the given the "data" path of the GraphQL response and
		 * convert the data to the target type. The path can be an operation
		 * root type name, e.g. "book", or a nested path such as "book.name",
		 * or any <a href="https://github.com/jayway/JsonPath">JsonPath</a>
		 * relative to the "data" key of the response.
		 * @param path a JSON path to the data of interest
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return the entity resulting from the conversion
		 */
		<D> D toEntity(String path, Class<D> entityType);

		/**
		 * Variant of {@link #toEntity(String, Class)} for entity classes with
		 * generic types.
		 * @param path a JSON path to the data of interest
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return the entity resulting from the conversion
		 */
		<D> D toEntity(String path, ParameterizedTypeReference<D> entityType);

		/**
		 * Switch to the given the "data" path of the GraphQL response and
		 * convert the data to a List with the given element type.
		 * The path can be an operation root type name, e.g. "book", or a
		 * nested path such as "book.name", or any
		 * <a href="https://github.com/jayway/JsonPath">JsonPath</a>
		 * relative to the "data" key of the response.
		 * @param path a JSON path to the data of interest
		 * @param elementType the type of element to convert to
		 * @param <D> the target entity type
		 * @return the list of entities resulting from the conversion
		 */
		<D> List<D> toEntityList(String path, Class<D> elementType);

		/**
		 * Variant of {@link #toEntityList(String, Class)} for entity classes
		 * with generic types.
		 * @param path a JSON path to the data of interest
		 * @param elementType the type to convert to
		 * @param <D> the target entity type
		 * @return the list of entities resulting from the conversion
		 */
		<D> List<D> toEntityList(String path, ParameterizedTypeReference<D> elementType);

		/**
		 * Return the errors from the response or an empty list.
		 */
		List<GraphQLError> errors();

	}

}