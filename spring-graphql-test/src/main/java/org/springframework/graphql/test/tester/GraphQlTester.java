/*
 * Copyright 2002-2021 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import graphql.GraphQLError;
import reactor.core.publisher.Flux;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Main entry point for testing GraphQL with requests performed either as an HTTP client
 * via {@link WebTestClient} or directly via a {@link WebGraphQlHandler}.
 *
 *
 * <p>
 * GraphQL requests to Spring MVC without an HTTP server: <pre class="code">
 * &#064;SpringBootTest
 * &#064;AutoConfigureMockMvc
 * public class MyTests {
 *
 *  private GraphQlTester graphQlTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired MockMvc mockMvc) {
 *      WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).baseUrl("/graphql").build();
 *      this.graphQlTester = GraphQlTester.create(client);
 *  }
 * </pre>
 *
 * <p>
 * GraphQL requests to Spring WebFlux without an HTTP server: <pre class="code">
 * &#064;SpringBootTest
 * &#064;AutoConfigureWebTestClient
 * public class MyTests {
 *
 *  private GraphQlTester graphQlTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired WebTestClient client) {
 *      this.graphQlTester = GraphQlTester.create(client);
 *  }
 * </pre>
 *
 * <p>
 * GraphQL requests to a running server: <pre class="code">
 * &#064;SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
 * public class MyTests {
 *
 *  private GraphQlTester graphQlTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired WebTestClient client) {
 *      this.graphQlTester = GraphQlTester.create(client);
 *  }
 * </pre>
 *
 * <p>
 * GraphQL requests to any {@link WebGraphQlHandler}: <pre class="code">
 * &#064;SpringBootTest
 * public class MyTests {
 *
 *  private GraphQlTester graphQlTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired WebGraphQLHandler handler) {
 *      this.graphQlTester = GraphQlTester.create(handler);
 *  }
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlTester {

	/**
	 * Prepare to perform a GraphQL request with the given operation which may be a query,
	 * mutation, or a subscription.
	 * @param query the operation to be performed
	 * @return spec for response assertions
	 * @throws AssertionError if the response status is not 200 (OK)
	 */
	RequestSpec query(String query);

	/**
	 * Create a {@code GraphQlTester} that performs GraphQL requests as an HTTP client
	 * through the given {@link WebTestClient}. Depending on how the {@code WebTestClient}
	 * is set up, tests may be with or without a server. See setup examples in class-level
	 * Javadoc.
	 * @param client the web client to perform requests with
	 * @return the created {@code GraphQlTester} instance
	 */
	static GraphQlTester create(WebTestClient client) {
		return new DefaultGraphQlTester(client);
	}

	/**
	 * Create a {@code GraphQlTester} that performs GraphQL requests through the given
	 * {@link WebGraphQlHandler}.
	 * @param handler the handler to execute requests with
	 * @return the created {@code GraphQlTester} instance
	 */
	static GraphQlTester create(WebGraphQlHandler handler) {
		return new DefaultGraphQlTester(handler);
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

		/**
		 * Modify variables by accessing the underlying map.
		 * @param variablesConsumer a callback for the map of variables
		 * @return this request spec
		 */
		RequestSpec variables(Consumer<Map<String, Object>> variablesConsumer);

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
		 * Assert the value at the given path does not exist or is empty as defined in
		 * {@link org.springframework.util.ObjectUtils#isEmpty(Object)}.
		 * @return spec to assert the converted entity with
		 * @see org.springframework.util.ObjectUtils#isEmpty(Object)
		 */
		PathSpec valueIsEmpty();

		/**
		 * Assert the value at the given path is not {@link #valueIsEmpty()}.
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
		 * Add a filter for expected errors. All errors that match the predicate are
		 * treated as expected and ignored on {@link #verify()} or when
		 * {@link TraverseSpec#path(String) traversing} to a data path.
		 * @param errorPredicate the predicate to add
		 * @return the same spec to add more filters before {@link #verify()}
		 */
		ErrorSpec filter(Predicate<GraphQLError> errorPredicate);

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
