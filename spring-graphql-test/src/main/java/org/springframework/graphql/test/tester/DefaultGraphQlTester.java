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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.RequestInput;
import org.springframework.lang.Nullable;
import org.springframework.test.util.AssertionErrors;
import org.springframework.test.util.JsonExpectationsHelper;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link GraphQlTester}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultGraphQlTester implements GraphQlTester {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = DefaultGraphQlTester.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}


	private final RequestStrategy requestStrategy;


	DefaultGraphQlTester(GraphQlService service) {
		this(new GraphQlServiceRequestStrategy(service, initJsonPathConfig()));
	}

	DefaultGraphQlTester(RequestStrategy requestStrategy) {
		this.requestStrategy = requestStrategy;
	}


	protected RequestStrategy getRequestStrategy() {
		return this.requestStrategy;
	}


	protected static Configuration initJsonPathConfig() {
		return (jackson2Present ? Jackson2Configuration.create() : Configuration.builder().build());
	}

	@Override
	public RequestSpec query(String query) {
		return new DefaultRequestSpec(this.requestStrategy, query);
	}

	/**
	 * Encapsulate how a GraphQL request is performed.
	 */
	interface RequestStrategy {

		/**
		 * Perform a request with the given {@link RequestInput} container.
		 * @param input the request input
		 * @return the response spec
		 */
		GraphQlTester.ResponseSpec execute(RequestInput input);

		/**
		 * Perform a subscription with the given {@link RequestInput} container.
		 * @param input the request input
		 * @return the subscription spec
		 */
		GraphQlTester.SubscriptionSpec executeSubscription(RequestInput input);

	}

	/**
	 * Base class for a {@link RequestStrategy} that perform GraphQL requests
	 * without an underlying transport and where {@link RequestInput} provides
	 * sufficient input.
	 */
	protected abstract static class AbstractDirectRequestStrategy implements RequestStrategy {

		protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

		private final Configuration jsonPathConfig;

		protected AbstractDirectRequestStrategy(Configuration jsonPathConfig) {
			this.jsonPathConfig = jsonPathConfig;
		}

		@Override
		public ResponseSpec execute(RequestInput input) {
			ExecutionResult executionResult = executeInternal(input);
			DocumentContext context = JsonPath.parse(executionResult.toSpecification(), this.jsonPathConfig);
			return new DefaultResponseSpec(context, assertDecorator(input));
		}

		@Override
		public SubscriptionSpec executeSubscription(RequestInput input) {
			ExecutionResult result = executeInternal(input);
			AssertionErrors.assertTrue("Subscription did not return Publisher", result.getData() instanceof Publisher);

			List<GraphQLError> errors = result.getErrors();
			Consumer<Runnable> assertDecorator = assertDecorator(input);
			assertDecorator.accept(() -> AssertionErrors.assertTrue(
					"Response has " + errors.size() + " unexpected error(s).", CollectionUtils.isEmpty(errors)));

			return new DefaultSubscriptionSpec(result.getData(), this.jsonPathConfig, assertDecorator);
		}

		/**
		 * Sub-classes implement this to actual perform the request.
		 */
		protected abstract ExecutionResult executeInternal(RequestInput input);

		private Consumer<Runnable> assertDecorator(RequestInput input) {
			return (assertion) -> {
				try {
					assertion.run();
				}
				catch (AssertionError ex) {
					throw new AssertionError(ex.getMessage() + "\nRequest: " + input, ex);
				}
			};
		}

	}

	/**
	 * {@link RequestStrategy} that performs requests through a {@link GraphQlService}.
	 */
	protected static class GraphQlServiceRequestStrategy extends AbstractDirectRequestStrategy {

		private final GraphQlService graphQlService;

		protected GraphQlServiceRequestStrategy(GraphQlService service, Configuration jsonPathConfig) {
			super(jsonPathConfig);
			this.graphQlService = service;
		}

		protected ExecutionResult executeInternal(RequestInput input) {
			ExecutionResult result = this.graphQlService.execute(input.toExecutionInput()).block(DEFAULT_TIMEOUT);
			Assert.notNull(result, "Expected ExecutionResult");
			return result;
		}
	}

	/**
	 * {@link RequestSpec} that collects the query, operationName, and variables.
	 */
	protected static class DefaultRequestSpec implements RequestSpec {

		private final RequestStrategy requestStrategy;

		private final String query;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		protected DefaultRequestSpec(RequestStrategy requestStrategy, String query) {
			Assert.notNull(requestStrategy, "RequestStrategy is required");
			Assert.notNull(query, "`query` is required");
			this.requestStrategy = requestStrategy;
			this.query = query;
		}

		@Override
		public RequestSpec operationName(@Nullable String name) {
			this.operationName = name;
			return this;
		}

		@Override
		public RequestSpec variable(String name, Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public RequestSpec variables(Consumer<Map<String, Object>> variablesConsumer) {
			variablesConsumer.accept(this.variables);
			return this;
		}

		@Override
		public ResponseSpec execute() {
			RequestInput input = createRequestInput();
			return this.requestStrategy.execute(input);
		}

		@Override
		public void executeAndVerify() {
			RequestInput input = createRequestInput();
			ResponseSpec spec = this.requestStrategy.execute(input);
			spec.path("$.errors").valueIsEmpty();
		}

		@Override
		public SubscriptionSpec executeSubscription() {
			RequestInput input = createRequestInput();
			return this.requestStrategy.executeSubscription(input);
		}

		protected RequestInput createRequestInput() {
			return new RequestInput(this.query, this.operationName, this.variables);
		}

	}

	private static class ErrorsContainer {

		private static final Predicate<GraphQLError> MATCH_ALL_PREDICATE = (error) -> true;

		private final List<TestGraphQlError> errors;

		private final Consumer<Runnable> assertDecorator;

		ErrorsContainer(List<TestGraphQlError> errors, Consumer<Runnable> assertDecorator) {
			Assert.notNull(errors, "`errors` is required");
			Assert.notNull(assertDecorator, "`assertDecorator` is required");
			this.errors = errors;
			this.assertDecorator = assertDecorator;
		}

		void doAssert(Runnable task) {
			this.assertDecorator.accept(task);
		}

		void filterErrors(Predicate<GraphQLError> errorPredicate) {
			this.errors.forEach((error) -> error.filter(errorPredicate));
		}

		void consumeErrors(Consumer<List<GraphQLError>> consumer) {
			filterErrors(MATCH_ALL_PREDICATE);
			consumer.accept(new ArrayList<>(this.errors));
		}

		void verifyErrors() {

			List<TestGraphQlError> unexpected = this.errors.stream().filter((error) -> !error.isExpected())
					.collect(Collectors.toList());

			this.assertDecorator
					.accept(() -> AssertionErrors.assertTrue(
							"Response has " + unexpected.size() + " unexpected error(s)"
									+ ((unexpected.size() != this.errors.size())
											? " of " + this.errors.size() + " total" : "")
									+ ". " + "If expected, please use ResponseSpec#errors to filter them out: "
									+ unexpected,
							CollectionUtils.isEmpty(unexpected)));
		}

	}

	/**
	 * Container for a GraphQL response with access to data and errors.
	 */
	private static class ResponseContainer extends ErrorsContainer {

		private static final JsonPath ERRORS_PATH = JsonPath.compile("$.errors");

		private final DocumentContext documentContext;

		private final String jsonContent;

		ResponseContainer(DocumentContext documentContext, Consumer<Runnable> assertDecorator) {
			super(readErrors(documentContext), assertDecorator);
			this.documentContext = documentContext;
			this.jsonContent = this.documentContext.jsonString();
		}

		private static List<TestGraphQlError> readErrors(DocumentContext documentContext) {
			Assert.notNull(documentContext, "DocumentContext is required");
			try {
				return documentContext.read(ERRORS_PATH, new TypeRef<List<TestGraphQlError>>() {
				});
			}
			catch (PathNotFoundException ex) {
				return Collections.emptyList();
			}
		}

		String jsonContent() {
			return this.jsonContent;
		}

		String jsonContent(JsonPath jsonPath) {
			try {
				Object content = this.documentContext.read(jsonPath);
				return this.documentContext.configuration().jsonProvider().toJson(content);
			}
			catch (Exception ex) {
				throw new AssertionError("JSON parsing error", ex);
			}
		}

		<T> T read(JsonPath jsonPath, TypeRef<T> typeRef) {
			return this.documentContext.read(jsonPath, typeRef);
		}

	}

	/**
	 * {@link ResponseSpec} that operates on the response from a GraphQL HTTP request.
	 */
	protected static final class DefaultResponseSpec implements ResponseSpec, ErrorSpec {

		private final ResponseContainer responseContainer;

		/**
		 * Class constructor.
		 * @param documentContext the parsed response content
		 * @param assertDecorator decorator to apply around assertions, e.g. to add extra
		 * contextual information such as HTTP request and response body details
		 */
		protected DefaultResponseSpec(DocumentContext documentContext, Consumer<Runnable> assertDecorator) {
			this.responseContainer = new ResponseContainer(documentContext, assertDecorator);
		}

		@Override
		public PathSpec path(String path) {
			this.responseContainer.verifyErrors();
			return new DefaultPathSpec(path, this.responseContainer);
		}

		@Override
		public ErrorSpec errors() {
			return this;
		}

		@Override
		public ErrorSpec filter(Predicate<GraphQLError> predicate) {
			this.responseContainer.filterErrors(predicate);
			return this;
		}

		@Override
		public TraverseSpec verify() {
			this.responseContainer.verifyErrors();
			return this;
		}

		@Override
		public TraverseSpec satisfy(Consumer<List<GraphQLError>> consumer) {
			this.responseContainer.consumeErrors(consumer);
			return this;
		}

	}

	/**
	 * {@link PathSpec} implementation.
	 */
	private static class DefaultPathSpec implements PathSpec {

		private final String inputPath;

		private final ResponseContainer responseContainer;

		private final JsonPath jsonPath;

		private final JsonPathExpectationsHelper pathHelper;

		DefaultPathSpec(String path, ResponseContainer responseContainer) {
			Assert.notNull(path, "`path` is required");
			Assert.notNull(responseContainer, "ResponseContainer is required");
			this.inputPath = path;
			this.responseContainer = responseContainer;
			this.jsonPath = initJsonPath(path);
			this.pathHelper = new JsonPathExpectationsHelper(this.jsonPath.getPath());
		}

		private static JsonPath initJsonPath(String path) {
			if (!StringUtils.hasText(path)) {
				path = "$.data";
			}
			else if (!path.startsWith("$") && !path.startsWith("data.")) {
				path = "$.data." + path;
			}
			return JsonPath.compile(path);
		}

		@Override
		public PathSpec path(String path) {
			return new DefaultPathSpec(path, this.responseContainer);
		}

		@Override
		public PathSpec pathExists() {
			this.responseContainer.doAssert(() -> this.pathHelper.hasJsonPath(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public PathSpec pathDoesNotExist() {
			this.responseContainer
					.doAssert(() -> this.pathHelper.doesNotHaveJsonPath(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public PathSpec valueExists() {
			this.responseContainer.doAssert(() -> this.pathHelper.exists(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public PathSpec valueDoesNotExist() {
			this.responseContainer.doAssert(() -> this.pathHelper.doesNotExist(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public PathSpec valueIsEmpty() {
			this.responseContainer.doAssert(() -> {
				try {
					this.pathHelper.assertValueIsEmpty(this.responseContainer.jsonContent());
				}
				catch (AssertionError ex) {
					// ignore
				}
			});
			return this;
		}

		@Override
		public PathSpec valueIsNotEmpty() {
			this.responseContainer
					.doAssert(() -> this.pathHelper.assertValueIsNotEmpty(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public <D> EntitySpec<D, ?> entity(Class<D> entityType) {
			D entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(entityType));
			return new DefaultEntitySpec<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public <D> EntitySpec<D, ?> entity(ParameterizedTypeReference<D> entityType) {
			D entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(entityType));
			return new DefaultEntitySpec<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public <D> ListEntitySpec<D> entityList(Class<D> elementType) {
			List<D> entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(List.class, elementType));
			return new DefaultListEntitySpec<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public <D> ListEntitySpec<D> entityList(ParameterizedTypeReference<D> elementType) {
			List<D> entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(List.class, elementType));
			return new DefaultListEntitySpec<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public PathSpec matchesJson(String expectedJson) {
			matchesJson(expectedJson, false);
			return this;
		}

		@Override
		public PathSpec matchesJsonStrictly(String expectedJson) {
			matchesJson(expectedJson, true);
			return this;
		}

		private void matchesJson(String expected, boolean strict) {
			this.responseContainer.doAssert(() -> {
				String actual = this.responseContainer.jsonContent(this.jsonPath);
				try {
					new JsonExpectationsHelper().assertJsonEqual(expected, actual, strict);
				}
				catch (AssertionError ex) {
					throw new AssertionError(ex.getMessage() + "\n\n" + "Expected JSON content:\n'" + expected + "'\n\n"
							+ "Actual JSON content:\n'" + actual + "'\n\n" + "Input path: '" + this.inputPath + "'\n",
							ex);
				}
				catch (Exception ex) {
					throw new AssertionError("JSON parsing error", ex);
				}
			});
		}

	}

	/**
	 * {@link EntitySpec} implementation.
	 */
	private static class DefaultEntitySpec<D, S extends EntitySpec<D, S>> implements EntitySpec<D, S> {

		private final D entity;

		private final ResponseContainer responseContainer;

		private final String inputPath;

		DefaultEntitySpec(D entity, ResponseContainer responseContainer, String path) {
			this.entity = entity;
			this.responseContainer = responseContainer;
			this.inputPath = path;
		}

		protected D getEntity() {
			return this.entity;
		}

		protected void doAssert(Runnable task) {
			this.responseContainer.doAssert(task);
		}

		protected String getInputPath() {
			return this.inputPath;
		}

		@Override
		public PathSpec path(String path) {
			return new DefaultPathSpec(path, this.responseContainer);
		}

		@Override
		public <T extends S> T isEqualTo(Object expected) {
			this.responseContainer.doAssert(() -> AssertionErrors.assertEquals(this.inputPath, expected, this.entity));
			return self();
		}

		@Override
		public <T extends S> T isNotEqualTo(Object other) {
			this.responseContainer.doAssert(() -> AssertionErrors.assertNotEquals(this.inputPath, other, this.entity));
			return self();
		}

		@Override
		public <T extends S> T isSameAs(Object expected) {
			this.responseContainer.doAssert(() -> AssertionErrors.assertTrue(this.inputPath, expected == this.entity));
			return self();
		}

		@Override
		public <T extends S> T isNotSameAs(Object other) {
			this.responseContainer.doAssert(() -> AssertionErrors.assertTrue(this.inputPath, other != this.entity));
			return self();
		}

		@Override
		public <T extends S> T matches(Predicate<D> predicate) {
			this.responseContainer
					.doAssert(() -> AssertionErrors.assertTrue(this.inputPath, predicate.test(this.entity)));
			return self();
		}

		@Override
		public <T extends S> T satisfies(Consumer<D> consumer) {
			this.responseContainer.doAssert(() -> consumer.accept(this.entity));
			return self();
		}

		@Override
		public D get() {
			return this.entity;
		}

		@SuppressWarnings("unchecked")
		private <T extends S> T self() {
			return (T) this;
		}

	}

	/**
	 * {@link ListEntitySpec} implementation.
	 */
	private static class DefaultListEntitySpec<E> extends DefaultEntitySpec<List<E>, ListEntitySpec<E>>
			implements ListEntitySpec<E> {

		DefaultListEntitySpec(List<E> entity, ResponseContainer responseContainer, String path) {
			super(entity, responseContainer, path);
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListEntitySpec<E> contains(E... elements) {
			doAssert(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue("List at path '" + getInputPath() + "' does not contain " + expected,
						(getEntity() != null && getEntity().containsAll(expected)));
			});
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListEntitySpec<E> doesNotContain(E... elements) {
			doAssert(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should not have contained " + expected,
						(getEntity() == null || !getEntity().containsAll(expected)));
			});
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListEntitySpec<E> containsExactly(E... elements) {
			doAssert(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should have contained exactly " + expected,
						(getEntity() != null && getEntity().containsAll(expected)));
			});
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSize(int size) {
			doAssert(() -> AssertionErrors.assertTrue("List at path '" + getInputPath() + "' should have size " + size,
					(getEntity() != null && getEntity().size() == size)));
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSizeLessThan(int boundary) {
			doAssert(() -> AssertionErrors.assertTrue(
					"List at path '" + getInputPath() + "' should have size less than " + boundary,
					(getEntity() != null && getEntity().size() < boundary)));
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSizeGreaterThan(int boundary) {
			doAssert(() -> AssertionErrors.assertTrue(
					"List at path '" + getInputPath() + "' should have size greater than " + boundary,
					(getEntity() != null && getEntity().size() > boundary)));
			return this;
		}

	}

	/**
	 * {@link SubscriptionSpec} implementation that operates on a {@link Publisher} of
	 * {@link ExecutionResult}.
	 */
	protected static class DefaultSubscriptionSpec implements SubscriptionSpec {

		private final Publisher<ExecutionResult> publisher;

		private final Configuration jsonPathConfig;

		private final Consumer<Runnable> assertDecorator;

		protected <T> DefaultSubscriptionSpec(Publisher<ExecutionResult> publisher, Configuration jsonPathConfig,
				Consumer<Runnable> decorator) {

			this.publisher = publisher;
			this.jsonPathConfig = jsonPathConfig;
			this.assertDecorator = decorator;
		}

		@Override
		public Flux<ResponseSpec> toFlux() {
			return Flux.from(this.publisher).map((result) -> {
				DocumentContext context = JsonPath.parse(result.toSpecification(), this.jsonPathConfig);
				return new DefaultResponseSpec(context, this.assertDecorator);
			});
		}

	}

	private static class Jackson2Configuration {

		static Configuration create() {
			return Configuration.builder().jsonProvider(new JacksonJsonProvider())
					.mappingProvider(new JacksonMappingProvider()).build();
		}

	}

}
