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
package org.springframework.graphql.test.query;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import graphql.GraphQLError;
import reactor.core.publisher.Flux;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.WebGraphQLService;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Main entry point for testing GraphQL with requests performed via
 * {@link WebTestClient} as an HTTP client or via any {@link WebGraphQLService}.
 *
 *
 * <p>GraphQL requests to Spring MVC without an HTTP server:
 * <pre class="code">
 * &#064;SpringBootTest
 * &#064;AutoConfigureMockMvc
 * public class MyTests {
 *
 *  private GraphQLTester graphQLTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired MockMvc mockMvc) {
 *      WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc).baseUrl("/graphql").build();
 *      this.graphQLTester = GraphQLTester.create(client);
 *  }
 * </pre>
 *
 * <p>GraphQL requests to Spring WebFlux without an HTTP server:
 * <pre class="code">
 * &#064;SpringBootTest
 * &#064;AutoConfigureWebTestClient
 * public class MyTests {
 *
 *  private GraphQLTester graphQLTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired WebTestClient client) {
 *      this.graphQLTester = GraphQLTester.create(client);
 *  }
 * </pre>
 *
 * <p>GraphQL requests to a running server:
 * <pre class="code">
 * &#064;SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
 * public class MyTests {
 *
 *  private GraphQLTester graphQLTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired WebTestClient client) {
 *      this.graphQLTester = GraphQLTester.create(client);
 *  }
 * </pre>
 *
 * <p>GraphQL requests to any {@link WebGraphQLService}:
 * <pre class="code">
 * &#064;SpringBootTest
 * public class MyTests {
 *
 *  private GraphQLTester graphQLTester;
 *
 *  &#064;BeforeEach
 *  public void setUp(&#064;Autowired WebGraphQLService service) {
 *      this.graphQLTester = GraphQLTester.create(service);
 *  }
 * </pre>
 */
public interface GraphQLTester {

	/**
	 * Prepare to perform a GraphQL request with the given query.
	 * @param query the query to send
	 * @return spec for response assertions
	 * @throws AssertionError if the response status is not 200 (OK)
	 */
	QuerySpec query(String query);


	/**
	 * Create a {@code GraphQLTester} that performs GraphQL requests as an HTTP
	 * client through the given {@link WebTestClient}. Depending on how the
	 * {@code WebTestClient} is set up, tests may be with or without a server.
	 * See setup examples in class-level Javadoc.
	 * @param client the web client to perform requests with
	 * @return the created {@code GraphQLTester} instance
	 */
	static GraphQLTester create(WebTestClient client) {
		return new DefaultGraphQLTester(client);
	}

	/**
	 * Create a {@code GraphQLTester} that performs GraphQL requests through the
	 * given {@link WebGraphQLService}.
	 * @param service the handler to execute requests with
	 * @return the created {@code GraphQLTester} instance
	 */
	static GraphQLTester create(WebGraphQLService service) {
		return new DefaultGraphQLTester(service);
	}


	/**
	 * Declare options to perform a GraphQL request.
	 */
	interface ExecuteSpec {

		/**
		 * Execute the GraphQL request and return a spec for further inspection
		 * of the response data and errors.
		 *
		 * @return options for asserting the response
		 * @throws AssertionError if the request is performed over HTTP and the
		 * response status is not 200 (OK).
		 */
		ResponseSpec execute();

		/**
		 * Perform the GraphQL request and then verify the GraphQL response does
		 * not contain any errors. To assert the errors, use {@link #execute()}
		 * instead.
		 */
		void executeAndVerify();

		/**
		 * Perform the GraphQL subscription request.
		 *
		 * @return options for assertions on subscription events
		 * @throws AssertionError if the request is performed over HTTP and the
		 * response status is not 200 (OK).
		 */
		SubscriptionSpec executeSubscription();

	}


	/**
	 * Declare options to gather input for a GraphQL query and execute it.
	 */
	interface QuerySpec extends ExecuteSpec {

		/**
		 * Set the operation name.
		 */
		QuerySpec operationName(@Nullable String name);

		/**
		 * Add a variable.
		 */
		QuerySpec variable(String name, Object value);

		/**
		 * Modify variables by accessing the underlying map.
		 */
		QuerySpec variables(Consumer<Map<String, Object>> variablesConsumer);
	}


	/**
	 * Declare options to switch to different part of the GraphQL response.
	 */
	interface TraverseSpec {

		/**
		 * Switch to a path under the "data" section of the GraphQL response.
		 * The path can be a query root type name, e.g. "project", or a nested
		 * path such as "project.name", or any
		 * <a href="https://github.com/jayway/JsonPath">JsonPath</a>.
		 *
		 * @param path the path to switch to
		 * @return spec for asserting the content under the given path
		 * @throws AssertionError if the GraphQL response contains
		 * <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a>
		 * that have not be checked via {@link ResponseSpec#errorsSatisfy(Consumer)}
		 */
		PathSpec path(String path);
	}


	/**
	 * Declare the first options available to insecpt a GraphQL response.
	 */
	interface ResponseSpec extends TraverseSpec {

		/**
		 * Inspect <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a>
		 * in the response, if any.
		 * <p>If this method is not used first, any attempts to check the data
		 * will result in an {@link AssertionError}. Therefore for GraphQL
		 * responses that are expected to have both data and errors, be sure
		 * to use this method first.
		 * @param errorConsumer the consumer to inspect errors with
		 * @return the same spec for further assertions on the data
		 */
		ResponseSpec errorsSatisfy(Consumer<List<GraphQLError>> errorConsumer);

	}


	/**
	 * Assertions available for the data at a given path.
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
		 * Assert a value exists at the given path where the value is any
		 * {@code non-null} value, possibly an empty array or map.
		 * @return spec to assert the converted entity with
		 */
		PathSpec valueExists();

		/**
		 * Assert a value does not {@link #valueExists() exist} at the given path.
		 * @return spec to assert the converted entity with
		 */
		PathSpec valueDoesNotExist();

		/**
		 * Assert the value at the given path does not exist or is empty as defined
		 * in {@link org.springframework.util.ObjectUtils#isEmpty(Object)}.
		 * @return spec to assert the converted entity with
		 * @see org.springframework.util.ObjectUtils#isEmpty(Object)
		 */
		PathSpec valueIsEmpty();

		/**
		 * Assert the value at the given path is not {@link #valueIsEmpty()}
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
		 * Parse the JSON at the given path and the given expected JSON and assert
		 * that the two are "similar".
		 * <p>Use of this option requires the
		 * <a href="https://jsonassert.skyscreamer.org/">JSONassert</a> library
		 * on to be on the classpath.
		 * @param expectedJson the expected JSON
		 * @return spec to specify a different path
		 * @see org.springframework.test.util.JsonExpectationsHelper#assertJsonEqual(String, String)
		 */
		TraverseSpec matchesJson(String expectedJson);

		/**
		 * Parse the JSON at the given path and the given expected JSON and assert
		 * that the two are "similar" so they contain the same attribute-value
		 * pairs regardless of formatting, along with lenient checking, e.g.
		 * extensible and non-strict array ordering.
		 * @param expectedJson the expected JSON
		 * @return spec to specify a different path
		 * @see org.springframework.test.util.JsonExpectationsHelper#assertJsonEqual(String, String, boolean)
		 */
		TraverseSpec matchesJsonStrictly(String expectedJson);

	}


	/**
	 * Declare options available to assert data converted to an entity.
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
		 * @return the same spec for more assertions
		 */
		<T extends S> T satisfies(Consumer<D> consumer);

		/**
		 * Return the converted entity.
		 */
		D get();

	}


	/**
	 * Extension of {@link EntitySpec} for a List of entities.
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
	 * Declare options available to assert a GraphQL Subscription response.
	 */
	interface SubscriptionSpec {

		/**
		 * Inspect <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a>
		 * in the response, if any.
		 * <p>If this method is not used first, any attempts to check event data
		 * will result in an {@link AssertionError}. Therefore for a GraphQL
		 * subscription that are expected to have both errors and events, be sure
		 * to use this method first.
		 * @param errorConsumer the consumer to inspect errors with
		 * @return the same spec for further assertions on the data
		 */
		SubscriptionSpec errorsSatisfy(Consumer<List<GraphQLError>> errorConsumer);

		/**
		 * Return a {@link Flux} of entities converted from some part of the data
		 * in each subscription event.
		 * @param path a path into the data of each subscription event
		 * @param entityType the type to convert data to
		 * @param <T> the entity type
		 * @return a {@code Flux} of entities that can be further inspected,
		 * e.g. with {@code reactor.test.StepVerifier}
		 */
		default <T> Flux<T> toFlux(String path, Class<T> entityType) {
			return toFlux().map(spec -> spec.path(path).entity(entityType).get());
		}

		/**
		 * Return a {@link Flux} of {@link ResponseSpec} instances, each
		 * representing an individual subscription event.
		 * @return a {@code Flux} of {@code ResponseSpec} instances that can be
		 * further inspected, e.g. with {@code reactor.test.StepVerifier}
		 */
		Flux<ResponseSpec> toFlux();

	}

}
