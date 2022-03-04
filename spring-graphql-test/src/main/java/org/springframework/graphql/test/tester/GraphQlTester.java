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

package org.springframework.graphql.test.tester;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import graphql.GraphQLError;
import reactor.core.publisher.Flux;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.lang.Nullable;

/**
 * Define a workflow to test GraphQL requests that is independent of the
 * underlying transport.
 *
 * <p>To test using a client that connects to a server, with or without a live
 * server, see {@code GraphQlTester} extensions:
 * <ul>
 * <li>{@link HttpGraphQlTester}
 * <li>{@link WebSocketGraphQlTester}
 * </ul>
 *
 * <p>To test on the server side, without a client, see the following:
 * <ul>
 * <li>{@link GraphQlServiceTester}
 * <li>{@link WebGraphQlTester}
 * </ul>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlTester {

	/**
	 * Start defining a GraphQL request with the given document, which is the
	 * textual representation of an operation (or operations) to perform,
	 * including selection sets and fragments.
	 * @param document the document for the request
	 * @return spec for response assertions
	 * @throws AssertionError if the response status is not 200 (OK)
	 */
	RequestSpec<?> document(String document);

	/**
	 * Variant of {@link #document(String)} that uses the given key to resolve
	 * the GraphQL document from a file, or in another way with the help of the
	 * {@link DocumentSource} that the client is configured with.
	 * @return spec for response assertions
	 * @throws IllegalArgumentException if the documentName cannot be resolved
	 * @throws AssertionError if the response status is not 200 (OK)
	 */
	RequestSpec<?> documentName(String documentName);

	/**
	 * Create a builder initialized from the configuration of "this" tester.
	 * Use it to build a new, independently configured instance.
	 */
	Builder<?> mutate();


	/**
	 * Create a builder with a custom {@code GraphQlTransport}.
	 * <p>For most cases, use a transport specific extension such as
	 * {@link HttpGraphQlTester} or {@link WebSocketGraphQlTester}. This method
	 * is for use with a custom {@code GraphQlTransport}.
	 * @param transport the transport to execute requests with
	 * @return the builder for further initialization
	 */
	static GraphQlTester.Builder<?> builder(GraphQlTransport transport) {
		return new DefaultGraphQlTester.Builder(transport);
	}


	/**
	 * A builder to create a {@link GraphQlTester} instance.
	 */
	interface Builder<B extends Builder<B>> {

		/**
		 * Configure a global {@link ErrorSpec#filter(Predicate) filter} that
		 * applies to all requests.
		 * @param predicate the error filter to add
		 * @return the same builder instance
		 */
		B errorFilter(Predicate<GraphQLError> predicate);

		/**
		 * Configure a {@link DocumentSource} for use with
		 * {@link #documentName(String)} for resolving a document by name.
		 * <p>By default, {@link ResourceDocumentSource} is used.
		 */
		B documentSource(DocumentSource contentLoader);

		/**
		 * Max amount of time to wait for a GraphQL response.
		 * <p>By default this is set to 5 seconds.
		 * @param timeout the response timeout value
		 */
		B responseTimeout(Duration timeout);

		/**
		 * Build the {@code GraphQlTester}.
		 * @return the created instance
		 */
		GraphQlTester build();
	}

	/**
	 * Declare options to perform a GraphQL request.
	 */
	interface ExecuteSpec {

		/**
		 * Execute the GraphQL request and return a spec for further inspection of
		 * response data and errors.
		 * @return options for asserting the response
		 * @throws AssertionError if the request is performed over HTTP and the response
		 * status is not 200 (OK).
		 */
		ResponseSpec execute();

		/**
		 * Execute the GraphQL request and verify the response contains no errors.
		 */
		void executeAndVerify();

		/**
		 * Execute the GraphQL request as a subscription and return a spec with options to
		 * transform the result stream.
		 * @return spec with options to transform the subscription result stream
		 * @throws AssertionError if the request is performed over HTTP and the response
		 * status is not 200 (OK).
		 */
		SubscriptionSpec executeSubscription();

	}

	/**
	 * Declare options to gather input for a GraphQL request and execute it.
	 */
	interface RequestSpec<T extends RequestSpec<T>> extends ExecuteSpec {

		/**
		 * Set the operation name.
		 * @param name the operation name
		 * @return this request spec
		 */
		T operationName(@Nullable String name);

		/**
		 * Add a variable.
		 * @param name the variable name
		 * @param value the variable value, possibly {@code null} since GraphQL
		 * supports providing null value vs not providing a value at all.
		 * @return this request spec
		 */
		T variable(String name, @Nullable Object value);

	}

	/**
	 * Declare options to switch to different part of the GraphQL response.
	 */
	interface TraverseSpec {

		/**
		 * Switch to a path under the "data" section of the GraphQL response. The path can
		 * be an operation root type name, e.g. "project", or a nested path such as
		 * "project.name", or any
		 * <a href="https://github.com/jayway/JsonPath">JsonPath</a>.
		 * @param path the path to switch to
		 * @return spec for asserting the content under the given path
		 * @throws AssertionError if the GraphQL response contains
		 * <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a> that have
		 * not be checked via {@link ResponseSpec#errors()}
		 */
		PathSpec path(String path);

	}

	/**
	 * Declare options to check the data and errors of a GraphQL response.
	 */
	interface ResponseSpec extends TraverseSpec {

		/**
		 * Return a spec to filter out or inspect errors. This must be used before
		 * traversing to a {@link #path(String)} if some errors are expected and need to
		 * be filtered out.
		 * @return the error spec
		 */
		ErrorSpec errors();

	}

	/**
	 * Declare options available to assert data at a given path.
	 */
	interface PathSpec extends TraverseSpec {

		/**
		 * Assert the given path exists, even if the value is {@code null}.
		 * @return spec to assert the converted entity with
		 */
		PathSpec pathExists();

		/**
		 * Assert the given path does not {@link #pathExists() exist}.
		 * @return spec to assert the converted entity with
		 */
		PathSpec pathDoesNotExist();

		/**
		 * Assert a value exists at the given path where the value is any {@code non-null}
		 * value, possibly an empty array or map.
		 * @return spec to assert the converted entity with
		 */
		PathSpec valueExists();

		/**
		 * Assert a value does not {@link #valueExists() exist} at the given path.
		 * @return spec to assert the converted entity with
		 */
		PathSpec valueDoesNotExist();

		/**
		 * Assert the value at the given path does exist but is empty as defined
		 * in {@link org.springframework.util.ObjectUtils#isEmpty(Object)}.
		 * @return spec to assert the converted entity with
		 * @see org.springframework.util.ObjectUtils#isEmpty(Object)
		 */
		PathSpec valueIsEmpty();

		/**
		 * Assert the value at the given path is not {@link #valueIsEmpty() empty}.
		 * @return spec to assert the converted entity with
		 */
		PathSpec valueIsNotEmpty();

		/**
		 * Convert the data at the given path to the target type.
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted entity with
		 */
		<D> EntitySpec<D, ?> entity(Class<D> entityType);

		/**
		 * Convert the data at the given path to the target type.
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted entity with
		 */
		<D> EntitySpec<D, ?> entity(ParameterizedTypeReference<D> entityType);

		/**
		 * Convert the data at the given path to a List of the target type.
		 * @param elementType the type of element to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted List of entities with
		 */
		<D> ListEntitySpec<D> entityList(Class<D> elementType);

		/**
		 * Convert the data at the given path to a List of the target type.
		 * @param elementType the type to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted List of entities with
		 */
		<D> ListEntitySpec<D> entityList(ParameterizedTypeReference<D> elementType);

		/**
		 * Parse the JSON at the given path and the given expected JSON and assert that
		 * the two are "similar".
		 * <p>
		 * Use of this option requires the
		 * <a href="https://jsonassert.skyscreamer.org/">JSONassert</a> library on to be
		 * on the classpath.
		 * @param expectedJson the expected JSON
		 * @return spec to specify a different path
		 * @see org.springframework.test.util.JsonExpectationsHelper#assertJsonEqual(String,
		 * String)
		 */
		TraverseSpec matchesJson(String expectedJson);

		/**
		 * Parse the JSON at the given path and the given expected JSON and assert that
		 * the two are "similar" so they contain the same attribute-value pairs regardless
		 * of formatting, along with lenient checking, e.g. extensible and non-strict
		 * array ordering.
		 * @param expectedJson the expected JSON
		 * @return spec to specify a different path
		 * @see org.springframework.test.util.JsonExpectationsHelper#assertJsonEqual(String,
		 * String, boolean)
		 */
		TraverseSpec matchesJsonStrictly(String expectedJson);

	}

	/**
	 * Declare options available to assert data converted to an entity.
	 *
	 * @param <D> the entity type
	 * @param <S> the spec type, including subtypes
	 */
	interface EntitySpec<D, S extends EntitySpec<D, S>> extends TraverseSpec {

		/**
		 * Assert the converted entity equals the given Object.
		 * @param expected the expected Object
		 * @param <T> the spec type
		 * @return the same spec for more assertions
		 */
		<T extends S> T isEqualTo(Object expected);

		/**
		 * Assert the converted entity does not equal the given Object.
		 * @param other the Object to check against
		 * @param <T> the spec type
		 * @return the same spec for more assertions
		 */
		<T extends S> T isNotEqualTo(Object other);

		/**
		 * Assert the converted entity is the same instance as the given Object.
		 * @param expected the expected Object
		 * @param <T> the spec type
		 * @return the same spec for more assertions
		 */
		<T extends S> T isSameAs(Object expected);

		/**
		 * Assert the converted entity is not the same instance as the given Object.
		 * @param other the Object to check against
		 * @param <T> the spec type
		 * @return the same spec for more assertions
		 */
		<T extends S> T isNotSameAs(Object other);

		/**
		 * Assert the converted entity matches the given predicate.
		 * @param predicate the expected Object
		 * @param <T> the spec type
		 * @return the same spec for more assertions
		 */
		<T extends S> T matches(Predicate<D> predicate);

		/**
		 * Perform any assertions on the converted entity, e.g. via AssertJ.
		 * @param consumer the consumer to inspect the entity with
		 * @param <T> the spec type
		 * @return the same spec for more assertions
		 */
		<T extends S> T satisfies(Consumer<D> consumer);

		/**
		 * Return the converted entity.
		 * @return the converter entity
		 */
		D get();

	}

	/**
	 * Extension of {@link EntitySpec} with options available to assert data converted to
	 * a List of entities.
	 *
	 * @param <E> the type of elements in the list
	 */
	interface ListEntitySpec<E> extends EntitySpec<List<E>, ListEntitySpec<E>> {

		/**
		 * Assert the list contains the given elements.
		 * @param elements values that are expected
		 * @return the same spec for more assertions
		 */
		@SuppressWarnings("unchecked")
		ListEntitySpec<E> contains(E... elements);

		/**
		 * Assert the list does not contain the given elements.
		 * @param elements values that are not expected
		 * @return the same spec for more assertions
		 */
		@SuppressWarnings("unchecked")
		ListEntitySpec<E> doesNotContain(E... elements);

		/**
		 * Assert the list contains the given elements.
		 * @param elements values that are expected
		 * @return the same spec for more assertions
		 */
		@SuppressWarnings("unchecked")
		ListEntitySpec<E> containsExactly(E... elements);

		/**
		 * Assert the list contains the specified number of elements.
		 * @param size the number of elements expected
		 * @return the same spec for more assertions
		 */
		ListEntitySpec<E> hasSize(int size);

		/**
		 * Assert the list contains fewer elements than the specified number.
		 * @param boundary the number to compare the number of elements to
		 * @return the same spec for more assertions
		 */
		ListEntitySpec<E> hasSizeLessThan(int boundary);

		/**
		 * Assert the list contains more elements than the specified number.
		 * @param boundary the number to compare the number of elements to
		 * @return the same spec for more assertions
		 */
		ListEntitySpec<E> hasSizeGreaterThan(int boundary);

	}

	/**
	 * Declare options to filter out expected errors or inspect all errors and verify
	 * there are no unexpected errors.
	 */
	interface ErrorSpec {

		/**
		 * Use this to filter out errors that are expected and can be ignored.
		 * This can be useful for warnings or other notifications returned along
		 * with the data.
		 * <p>The configured filters are applied to all errors. Those that match
		 * are treated as expected and are ignored on {@link #verify()} or when
		 * {@link TraverseSpec#path(String) traversing} to a data path.
		 * <p>In contrast to {@link #expect(Predicate)}, filters do not have to
		 * match any errors, and don't imply that the errors must be present.
		 * @param errorPredicate the error filter to add
		 * @return the same spec to add more filters before {@link #verify()}
		 */
		ErrorSpec filter(Predicate<GraphQLError> errorPredicate);

		/**
		 * Use this to declare errors that are expected.
		 * <p>Errors that match are treated as expected and are ignored on
		 * {@link #verify()} or when {@link TraverseSpec#path(String) traversing}
		 * to a data path.
		 * <p>In contrast to {@link #filter(Predicate)}, use of this option
		 * does imply that errors are  present or else an {@link AssertionError}
		 * is raised.
		 * @param errorPredicate the predicate for the expected error
		 * @return the same spec to add more filters or expected errors
		 */
		ErrorSpec expect(Predicate<GraphQLError> errorPredicate);

		/**
		 * Verify there are either no errors or that there no unexpected errors that have
		 * not been {@link #filter(Predicate) filtered out}.
		 * @return a spec to switch to a data path
		 */
		TraverseSpec verify();

		/**
		 * Inspect <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a> in
		 * the response, if any. Use of this method effectively suppresses all errors and
		 * allows {@link TraverseSpec#path(String) traversing} to a data path.
		 * @param errorsConsumer to inspect errors with
		 * @return a spec to switch to a data path
		 */
		TraverseSpec satisfy(Consumer<List<GraphQLError>> errorsConsumer);

	}

	/**
	 * Declare options available to assert a GraphQL Subscription response.
	 */
	interface SubscriptionSpec {

		/**
		 * Return a {@link Flux} of entities converted from some part of the data in each
		 * subscription event.
		 * @param path a path into the data of each subscription event
		 * @param entityType the type to convert data to
		 * @param <T> the entity type
		 * @return a {@code Flux} of entities that can be further inspected, e.g. with
		 * {@code reactor.test.StepVerifier}
		 */
		default <T> Flux<T> toFlux(String path, Class<T> entityType) {
			return toFlux().map((spec) -> spec.path(path).entity(entityType).get());
		}

		/**
		 * Return a {@link Flux} of {@link ResponseSpec} instances, each representing an
		 * individual subscription event.
		 * @return a {@code Flux} of {@code ResponseSpec} instances that can be further
		 * inspected, e.g. with {@code reactor.test.StepVerifier}
		 */
		Flux<ResponseSpec> toFlux();

	}

}
