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
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.graphql.client.GraphQlClient;
import reactor.core.publisher.Flux;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.ResponseError;
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
 * <li>{@link ExecutionGraphQlServiceTester}
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
	Request<?> document(String document);

	/**
	 * Variant of {@link #document(String)} that uses the given key to resolve
	 * the GraphQL document from a file with the help of the configured
	 * {@link Builder#documentSource(DocumentSource) DocumentSource}.
	 * @return spec for response assertions
	 * @throws IllegalArgumentException if the documentName cannot be resolved
	 * @throws AssertionError if the response status is not 200 (OK)
	 */
	Request<?> documentName(String documentName);

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
		return new DefaultTransportGraphQlTesterBuilder(transport);
	}


	/**
	 * A builder to create a {@link GraphQlTester} instance.
	 */
	interface Builder<B extends Builder<B>> {

		/**
		 * Configure a global {@link Errors#filter(Predicate) filter} that
		 * applies to all requests.
		 * @param predicate the error filter to add
		 * @return the same builder instance
		 */
		B errorFilter(Predicate<ResponseError> predicate);

		/**
		 * Configure a {@link DocumentSource} for use with
		 * {@link #documentName(String)} for resolving a document by name.
		 * <p>By default, this is set to {@link ResourceDocumentSource} with
		 * classpath location {@code "graphql-test/"} and
		 * {@link ResourceDocumentSource#FILE_EXTENSIONS} as extensions.
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
	 * Declare options to gather input for a GraphQL request and execute it.
	 */
	interface Request<T extends Request<T>> {

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

        T fileVariable(String name, Object value);

        T fileVariables(Map<String, Object> variables);

		/**
		 * Add a value for a protocol extension.
		 * @param name the protocol extension name
		 * @param value the extension value
		 * @return this request spec
		 */
		T extension(String name, @Nullable Object value);

		/**
		 * Execute the GraphQL request and return a spec for further inspection of
		 * response data and errors.
		 * @return options for asserting the response
		 * @throws AssertionError if the request is performed over HTTP and the response
		 * status is not 200 (OK).
		 */
		Response execute();

        void executeFileUploadAndVerify();

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
		Subscription executeSubscription();

        Response executeFileUpload();

	}

	/**
	 * Declare options to switch to different part of the GraphQL response.
	 */
	interface Traversable {

		/**
		 * Switch to a path under the "data" section of the GraphQL response. The path can
		 * be an operation root type name, e.g. "project", or a nested path such as
		 * "project.name", or any
		 * <a href="https://github.com/jayway/JsonPath">JsonPath</a>.
		 * @param path the path to switch to
		 * @return spec for asserting the content under the given path
		 * @throws AssertionError if the GraphQL response contains
		 * <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a> that have
		 * not be checked via {@link Response#errors()}
		 */
		Path path(String path);

	}

	/**
	 * Declare options to check the data and errors of a GraphQL response.
	 */
	interface Response extends Traversable {

		/**
		 * Return a spec to filter out or inspect errors. This must be used before
		 * traversing to a {@link #path(String)} if some errors are expected and need to
		 * be filtered out.
		 * @return the error spec
		 */
		Errors errors();

	}

	/**
	 * Options available to assert the response values at the current path.
	 */
	interface Path extends Traversable {

		/**
		 * Verify there is a {@code non-null} value or a non-empty list at the current path.
		 * @return the same {@code Path} spec for further assertions
		 */
		Path hasValue();

		/**
		 * Verify there is a {@code null} value at the current path.
		 */
		Path valueIsNull();

		/**
		 * Verify the current path does not exist.
		 * @return the same {@code Path} spec for further assertions
		 */
		Path pathDoesNotExist();

		/**
		 * Convert the data at the given path to the target type.
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return an {@code Entity} spec to verify the decoded value with
		 */
		<D> Entity<D, ?> entity(Class<D> entityType);

		/**
		 * Convert the data at the given path to the target type.
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return an {@code Entity} spec to verify the decoded value with
		 */
		<D> Entity<D, ?> entity(ParameterizedTypeReference<D> entityType);

		/**
		 * Convert the data at the given path to a List of the target type.
		 * @param elementType the type of element to convert to
		 * @param <D> the target entity type
		 * @return an {@code EntityList} spec to verify the decoded values with
		 */
		<D> EntityList<D> entityList(Class<D> elementType);

		/**
		 * Convert the data at the given path to a List of the target type.
		 * @param elementType the type to convert to
		 * @param <D> the target entity type
		 * @return an {@code EntityList} spec to verify the decoded values with
		 */
		<D> EntityList<D> entityList(ParameterizedTypeReference<D> elementType);

		/**
		 * Parse the JSON at the given path and the given expected JSON and assert that
		 * the two are "similar".
		 * <p>
		 * Use of this option requires the
		 * <a href="https://jsonassert.skyscreamer.org/">JSONassert</a> library on to be
		 * on the classpath.
		 * @param expectedJson the expected JSON
		 * @return {@code Traversable} spec to select a different path
		 * @see org.springframework.test.util.JsonExpectationsHelper#assertJsonEqual(String,
		 * String)
		 */
		Traversable matchesJson(String expectedJson);

		/**
		 * Parse the JSON at the given path and the given expected JSON and assert that
		 * the two are "similar" so they contain the same attribute-value pairs regardless
		 * of formatting, along with lenient checking, e.g. extensible and non-strict
		 * array ordering.
		 * @param expectedJson the expected JSON
		 * @return {@code Traversable} spec to select a different path
		 * @see org.springframework.test.util.JsonExpectationsHelper#assertJsonEqual(String,
		 * String, boolean)
		 */
		Traversable matchesJsonStrictly(String expectedJson);

	}

	/**
	 * Contains a decoded entity and provides options to assert it
	 *
	 * @param <D> the entity type
	 * @param <S> the {@code Entity} spec type
	 */
	interface Entity<D, S extends Entity<D, S>> extends Traversable {

		/**
		 * Verify the decoded entity is equal to the given value.
		 * @param expected the expected value
		 * @return the {@code Entity} spec for further assertions
		 */
		<T extends S> T isEqualTo(Object expected);

		/**
		 * Verify the decoded entity is not equal to the given value.
		 * @param other the value to check against
		 * @return the {@code Entity} spec for further assertions
		 */
		<T extends S> T isNotEqualTo(Object other);

		/**
		 * Verify the decoded entity is the same instance as the given value.
		 * @param expected the expected value
		 * @return the {@code Entity} spec for further assertions
		 */
		<T extends S> T isSameAs(Object expected);

		/**
		 * Verify the decoded entity is not the same instance as the given value.
		 * @param other the value to check against
		 * @return the {@code Entity} spec for further assertions
		 */
		<T extends S> T isNotSameAs(Object other);

		/**
		 * Verify the decoded entity matches the given predicate.
		 * @param predicate the predicate to apply
		 * @return the {@code Entity} spec for further assertions
		 */
		<T extends S> T matches(Predicate<D> predicate);

		/**
		 * Verify the entity with the given {@link Consumer}.
		 * @param consumer the consumer to apply
		 * @return the {@code Entity} spec for further assertions
		 */
		<T extends S> T satisfies(Consumer<D> consumer);

		/**
		 * Return the decoded entity value(s).
		 */
		D get();

	}

	/**
	 * Contains a List of decoded entities and provides options to assert them.
	 *
	 * @param <E> the type of elements in the list
	 */
	interface EntityList<E> extends Entity<List<E>, EntityList<E>> {

		/**
		 * Verify the list contains the given values, in any order.
		 * @param values values that are expected
		 * @return the {@code EntityList} spec for further assertions
		 */
		@SuppressWarnings("unchecked")
		EntityList<E> contains(E... values);

		/**
		 * Verify the list does not contain the given values.
		 * @param values the values that are not expected
		 * @return the {@code EntityList} spec for further assertions
		 */
		@SuppressWarnings("unchecked")
		EntityList<E> doesNotContain(E... values);

		/**
		 * Verify that the list contains exactly the given values and nothing
		 * else, in the same order.
		 * @param values the expected values
		 * @return the {@code EntityList} spec for further assertions
		 */
		@SuppressWarnings("unchecked")
		EntityList<E> containsExactly(E... values);

		/**
		 * Verify the number of values in the list.
		 * @param size the expected size
		 * @return the {@code EntityList} spec for further assertions
		 */
		EntityList<E> hasSize(int size);

		/**
		 * Verify the list has fewer than the number of values.
		 * @param size the number to compare the actual size to
		 * @return the {@code EntityList} spec for further assertions
		 */
		EntityList<E> hasSizeLessThan(int size);

		/**
		 * Verify the list has more than the specified number of values.
		 * @param size the number to compare the actual size to
		 * @return the {@code EntityList} spec for further assertions
		 */
		EntityList<E> hasSizeGreaterThan(int size);

	}

	/**
	 * Declare options to filter out expected errors or inspect all errors and verify
	 * there are no unexpected errors.
	 */
	interface Errors {

		/**
		 * Use this to filter out errors that are expected and can be ignored.
		 * This can be useful for warnings or other notifications returned along
		 * with the data.
		 * <p>The configured filters are applied to all errors. Those that match
		 * are treated as expected and are ignored on {@link #verify()} or when
		 * {@link Traversable#path(String) traversing} to a data path.
		 * <p>In contrast to {@link #expect(Predicate)}, filters do not have to
		 * match any errors, and don't imply that the errors must be present.
		 * @param errorPredicate the error filter to add
		 * @return the same spec to add more filters before {@link #verify()}
		 */
		Errors filter(Predicate<ResponseError> errorPredicate);

		/**
		 * Use this to declare errors that are expected.
		 * <p>Errors that match are treated as expected and are ignored on
		 * {@link #verify()} or when {@link Traversable#path(String) traversing}
		 * to a data path.
		 * <p>In contrast to {@link #filter(Predicate)}, use of this option
		 * does imply that errors are  present or else an {@link AssertionError}
		 * is raised.
		 * @param errorPredicate the predicate for the expected error
		 * @return the same spec to add more filters or expected errors
		 */
		Errors expect(Predicate<ResponseError> errorPredicate);

		/**
		 * Verify there are either no errors or that there no unexpected errors that have
		 * not been {@link #filter(Predicate) filtered out}.
		 * @return a spec to switch to a data path
		 */
		Traversable verify();

		/**
		 * Inspect <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a> in
		 * the response, if any. Use of this method effectively suppresses all errors and
		 * allows {@link Traversable#path(String) traversing} to a data path.
		 * @param errorsConsumer to inspect errors with
		 * @return a spec to switch to a data path
		 */
		Traversable satisfy(Consumer<List<ResponseError>> errorsConsumer);

	}

	/**
	 * Declare options available to assert a GraphQL Subscription response.
	 */
	interface Subscription {

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
		 * Return a {@link Flux} of {@link Response} instances, each representing an
		 * individual subscription event.
		 * @return a {@code Flux} of {@code ResponseSpec} instances that can be further
		 * inspected, e.g. with {@code reactor.test.StepVerifier}
		 */
		Flux<Response> toFlux();

	}

}
