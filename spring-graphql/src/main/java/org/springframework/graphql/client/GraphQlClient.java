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
import java.util.Map;
import java.util.function.Consumer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseField;
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
	RequestSpec document(String document);

	/**
	 * Variant of {@link #document(String)} that uses the given key to resolve
	 * the GraphQL document from a file with the help of the configured
	 * {@link Builder#documentSource(DocumentSource) DocumentSource}.
	 * @throws IllegalArgumentException if the content could not be loaded
	 */
	RequestSpec documentName(String name);

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
		return new DefaultTransportGraphQlClientBuilder(transport);
	}


	/**
	 * Defines a builder for creating {@link GraphQlClient} instances.
	 */
	interface Builder<B extends Builder<B>> {

		/**
		 * Configure interceptors to be invoked before delegating to the
		 * {@link GraphQlTransport} to perform the request.
		 * @param interceptors the interceptors to add
		 * @return this builder
		 */
		B interceptor(GraphQlClientInterceptor... interceptors);

		/**
		 * Customize the list of interceptors. The provided list is "live", so
		 * the consumer can inspect and insert interceptors accordingly.
		 * @param interceptorsConsumer consumer to customize the interceptors with
		 * @return this builder
		 */
		B interceptors(Consumer<List<GraphQlClientInterceptor>> interceptorsConsumer);

		/**
		 * Configure a {@link DocumentSource} for use with
		 * {@link #documentName(String)} for resolving a document by name.
		 * <p>By default, this is set to {@link ResourceDocumentSource} with
		 * classpath location {@code "graphql-documents/"} and
		 * {@link ResourceDocumentSource#FILE_EXTENSIONS} as extensions.
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
	interface RequestSpec {

		/**
		 * Set the name of the operation in the {@link #document(String) document}
		 * to execute, if the document contains multiple operations.
		 * @param operationName the operation name
		 * @return this request spec
		 */
		RequestSpec operationName(@Nullable String operationName);

		/**
		 * Add a value for a variable defined by the operation.
		 * @param name the variable name
		 * @param value the variable value
		 * @return this request spec
		 */
		RequestSpec variable(String name, @Nullable Object value);

		/**
		 * Add all given values for variables defined by the operation.
		 * @param variables the variable values
		 * @return this request spec
		 */
		RequestSpec variables(Map<String, Object> variables);

        RequestSpec fileVariable(String name, Object value);

        RequestSpec fileVariables(Map<String, Object> variables);

		/**
		 * Add a value for a protocol extension.
		 * @param name the protocol extension name
		 * @param value the extension value
		 * @return this request spec
		 */
		RequestSpec extension(String name, @Nullable Object value);

		/**
		 * Add all given protocol extensions.
		 * @param extensions the protocol extensions
		 * @return this request spec
		 */
		RequestSpec extensions(Map<String, Object> extensions);

		/**
		 * Set a client request attribute.
		 * <p>This is purely for client side request processing, i.e. available
		 * throughout the {@link GraphQlClientInterceptor} chain but not sent.
		 * @param name the name of the attribute
		 * @param value the attribute value
		 * @return this builder
		 */
		RequestSpec attribute(String name, Object value);

		/**
		 * Manipulate the client request attributes. The map provided to the consumer
		 * is "live", so the consumer can inspect and modify attributes accordingly.
		 * @param attributesConsumer consumer to customize attributes with
		 * @return this builder
		 */
		RequestSpec attributes(Consumer<Map<String, Object>> attributesConsumer);

		/**
		 * Shortcut for {@link #execute()} with a single field path to decode from.
		 * @return a spec with decoding options
		 * @throws FieldAccessException if the target field has any errors,
		 * including nested errors.
		 */
		RetrieveSpec retrieve(String path);

		/**
		 * Shortcut for {@link #executeSubscription()} with a single field path to decode from.
		 * @return a spec with decoding options
		 */
		RetrieveSubscriptionSpec retrieveSubscription(String path);

		/**
		 * Execute request with a single response, e.g. "query" or "mutation", and
		 * return a response for further options.
		 * @return a {@code Mono} with a {@code ClientGraphQlResponse} for further
		 * decoding of the response. The {@code Mono} may end wth an error due
		 * to transport level issues.
		 */
		Mono<ClientGraphQlResponse> execute();

        Mono<ClientGraphQlResponse> executeFileUpload();

        /**
		 * Execute a "subscription" request and return a stream of responses.
		 * @return a {@code Flux} with responses that provide further options for
		 * decoding of each response. The {@code Flux} may terminate as follows:
		 * <ul>
		 * <li>Completes if the subscription completes before the connection is closed.
		 * <li>{@link SubscriptionErrorException} if the subscription ends with an error.
		 * <li>{@link WebSocketDisconnectedException} if the connection is closed or
		 * lost before the stream terminates.
		 * <li>Exception for connection and GraphQL session initialization issues.
		 * </ul>
		 * <p>The {@code Flux} may be cancelled to notify the server to end the
		 * subscription stream.
		 */
		Flux<ClientGraphQlResponse> executeSubscription();

	}


	/**
	 * Declares options to decode a field for a single response operation.
	 */
	interface RetrieveSpec {

		/**
		 * Decode the field to an entity of the given type.
		 * @param entityType the type to convert to
		 * @return {@code Mono} with the decoded entity. Completes empty when
		 * the field is {@code null} without errors, or ends with
		 * {@link FieldAccessException} for an invalid response or a failed field
		 * @see GraphQlResponse#isValid()
		 * @see ResponseField#getError()
		 */
		<D> Mono<D> toEntity(Class<D> entityType);

		/**
		 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
		 */
		<D> Mono<D> toEntity(ParameterizedTypeReference<D> entityType);

		/**
		 * Decode the field to a list of entities with the given type.
		 * @param elementType the type of elements in the list
		 * @return {@code Mono} with a list of decoded entities, possibly an
		 * empty list, or ends with {@link FieldAccessException} if the target
		 * field is not present or has no value.
		 * @see GraphQlResponse#isValid()
		 * @see ResponseField#getError()
		 */
		<D> Mono<List<D>> toEntityList(Class<D> elementType);

		/**
		 * Variant of {@link #toEntityList(Class)} with {@link ParameterizedTypeReference}.
		 */
		<D> Mono<List<D>> toEntityList(ParameterizedTypeReference<D> elementType);

	}


	/**
	 * Declares options to decode a field in each response of a subscription.
	 */
	interface RetrieveSubscriptionSpec {

		/**
		 * Decode the field to an entity of the given type.
		 * @param entityType the type to convert to
		 * @return a stream of decoded entities, one for each response, excluding
		 * responses in which the field is {@code null} without errors. Ends with
		 * {@link FieldAccessException} for an invalid response or a failed field.
		 * May also end with a {@link GraphQlTransportException}.
		 * @see GraphQlResponse#isValid()
		 * @see ResponseField#getError()
		 */
		<D> Flux<D> toEntity(Class<D> entityType);

		/**
		 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
		 */
		<D> Flux<D> toEntity(ParameterizedTypeReference<D> entityType);

		/**
		 * Decode the field to a list of entities with the given type.
		 * @param elementType the type of elements in the list
		 * @return lists of decoded entities, one for each response, excluding
		 * responses in which the field is {@code null} without errors. Ends with
		 * {@link FieldAccessException} for an invalid response or a failed field.
		 * May also end with a {@link GraphQlTransportException}.
		 * @see GraphQlResponse#isValid()
		 * @see ResponseField#getError()
		 */
		<D> Flux<List<D>> toEntityList(Class<D> elementType);

		/**
		 * Variant of {@link #toEntityList(Class)} with {@link ParameterizedTypeReference}.
		 */
		<D> Flux<List<D>> toEntityList(ParameterizedTypeReference<D> elementType);

	}

}