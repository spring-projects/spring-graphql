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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.RequestInput;
import org.springframework.graphql.WebGraphQLService;
import org.springframework.graphql.WebInput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.test.util.AssertionErrors;
import org.springframework.test.util.JsonExpectationsHelper;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link GraphQLTester}.
 */
class DefaultGraphQLTester implements GraphQLTester {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = DefaultGraphQLTester.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader) &&
				ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}


	private final RequestStrategy requestStrategy;

	private final Configuration jsonPathConfig;


	DefaultGraphQLTester(WebTestClient client) {
		this.jsonPathConfig = initJsonPathConfig();
		this.requestStrategy = new WebTestClientRequestStrategy(client, this.jsonPathConfig);
	}

	DefaultGraphQLTester(WebGraphQLService service) {
		this.jsonPathConfig = initJsonPathConfig();
		this.requestStrategy = new DirectRequestStrategy(service, this.jsonPathConfig);
	}

	private Configuration initJsonPathConfig() {
		return (jackson2Present ? Jackson2Configuration.create() : Configuration.builder().build());
	}


	@Override
	public QuerySpec query(String query) {
		return new DefaultQuerySpec(query);
	}


	/**
	 * Encapsulate how a GraphQL request is performed.
	 */
	interface RequestStrategy {

		/**
		 * Perform a query with the given {@link RequestInput} container.
		 */
		GraphQLTester.ResponseSpec execute(RequestInput input);

		/**
		 * Perform a subscription with the given {@link RequestInput} container.
		 */
		GraphQLTester.SubscriptionSpec executeSubscription(RequestInput input);

	}


	/**
	 * {@link RequestStrategy} that works as an HTTP client with requests
	 * executed through {@link WebTestClient} that in turn may work connect with
	 * or without a live server for Spring MVC and WebFlux.
	 */
	private static class WebTestClientRequestStrategy implements RequestStrategy {

		private final WebTestClient client;

		private final Configuration jsonPathConfig;

		WebTestClientRequestStrategy(WebTestClient client, Configuration jsonPathConfig) {
			this.client = client;
			this.jsonPathConfig = jsonPathConfig;
		}

		@Override
		public ResponseSpec execute(RequestInput requestInput) {
			EntityExchangeResult<byte[]> result = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(requestInput)
					.exchange()
					.expectStatus().isOk()
					.expectHeader().contentType(MediaType.APPLICATION_JSON)
					.expectBody()
					.returnResult();

			byte[] bytes = result.getResponseBodyContent();
			Assert.notNull(bytes, "Expected GraphQL response content");
			String content = new String(bytes, StandardCharsets.UTF_8);
			DocumentContext documentContext = JsonPath.parse(content, this.jsonPathConfig);

			return new DefaultResponseSpec(documentContext, result::assertWithDiagnostics);
		}

		@Override
		public SubscriptionSpec executeSubscription(RequestInput queryInput) {
			FluxExchangeResult<TestExecutionResult> result = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.bodyValue(queryInput)
					.exchange()
					.expectStatus().isOk()
					.expectHeader().contentType(MediaType.TEXT_EVENT_STREAM)
					.returnResult(TestExecutionResult.class);

			return new DefaultSubscriptionSpec(
					result.getResponseBody().cast(ExecutionResult.class),
					Collections.emptyList(), this.jsonPathConfig,
					result::assertWithDiagnostics);
		}
	}


	/**
	 * {@link RequestStrategy} that performs requests directly on {@link GraphQL}.
	 */
	private static class DirectRequestStrategy implements RequestStrategy {

		private static final URI DEFAULT_URL = URI.create("http://localhost:8080/graphql");

		private static final HttpHeaders DEFAULT_HEADERS = new HttpHeaders();

		private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);


		private final WebGraphQLService graphQLService;

		private final Configuration jsonPathConfig;

		public DirectRequestStrategy(WebGraphQLService service, Configuration jsonPathConfig) {
			this.graphQLService = service;
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
			return new DefaultSubscriptionSpec(
					result.getData(), result.getErrors(), this.jsonPathConfig, assertDecorator(input));
		}

		private ExecutionResult executeInternal(RequestInput input) {
			WebInput webInput = new WebInput(DEFAULT_URL, DEFAULT_HEADERS, input.toMap(), null);
			ExecutionResult result = this.graphQLService.execute(webInput).block(DEFAULT_TIMEOUT);
			Assert.notNull(result, "Expected ExecutionResult");
			return result;
		}

		private Consumer<Runnable> assertDecorator(RequestInput input) {
			return assertion -> {
				try {
					assertion.run();
				}
				catch (AssertionError ex) {
					throw new AssertionError(ex.getMessage() + "\nQuery: " + input, ex);
				}
			};
		}
	}


	/**
	 * {@link QuerySpec} that collects the query, operationName, and variables.
	 */
	private class DefaultQuerySpec implements QuerySpec {

		private final String query;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private DefaultQuerySpec(String query) {
			Assert.notNull(query, "`query` is required");
			this.query = query;
		}

		@Override
		public QuerySpec operationName(@Nullable String name) {
			this.operationName = name;
			return this;
		}

		@Override
		public QuerySpec variable(String name, Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public QuerySpec variables(Consumer<Map<String, Object>> variablesConsumer) {
			variablesConsumer.accept(this.variables);
			return this;
		}

		@Override
		public ResponseSpec execute() {
			RequestInput input = new RequestInput(this.query, this.operationName, this.variables);
			return DefaultGraphQLTester.this.requestStrategy.execute(input);
		}

		@Override
		public void executeAndVerify() {
			RequestInput input = new RequestInput(this.query, this.operationName, this.variables);
			ResponseSpec spec = DefaultGraphQLTester.this.requestStrategy.execute(input);
			spec.path("$.errors").valueIsEmpty();
		}

		@Override
		public SubscriptionSpec executeSubscription() {
			RequestInput input = new RequestInput(this.query, this.operationName, this.variables);
			return DefaultGraphQLTester.this.requestStrategy.executeSubscription(input);
		}
	}


	/**
	 * Base for a {@link ResponseSpec} implementations.
	 */
	private static class ResponseSpecSupport {

		private final List<GraphQLError> errors;

		private boolean errorsChecked;

		private final Consumer<Runnable> assertDecorator;

		private ResponseSpecSupport(List<GraphQLError> errors, Consumer<Runnable> assertDecorator) {
			this.errors = errors;
			this.assertDecorator = assertDecorator;
		}

		protected Consumer<Runnable> getAssertDecorator() {
			return this.assertDecorator;
		}

		protected void consumeErrors(Consumer<List<GraphQLError>> errorConsumer) {
			this.errorsChecked = true;
			errorConsumer.accept(this.errors);
		}

		protected void assertErrorsEmptyOrConsumed() {
			if (!this.errorsChecked) {
				this.assertDecorator.accept(() -> AssertionErrors.assertTrue(
						"Response contains GraphQL errors. " +
								"To avoid this message, please use ResponseSpec#errorsSatisfy to check them.",
						CollectionUtils.isEmpty(this.errors)));
			}
		}
	}


	/**
	 * {@link ResponseSpec} that operates on the response from a GraphQL HTTP request.
	 */
	private static class DefaultResponseSpec extends ResponseSpecSupport implements ResponseSpec {

		private static final JsonPath ERRORS_PATH = JsonPath.compile("$.errors");


		private final DocumentContext documentContext;

		/**
		 * Class constructor.
		 * @param documentContext the parsed response content
		 * @param assertDecorator decorator to apply around assertions, e.g. to
		 * add extra contextual information such as HTTP request and response
		 * body details
		 */
		private DefaultResponseSpec(DocumentContext documentContext, Consumer<Runnable> assertDecorator) {
			super(initErrors(documentContext), assertDecorator);
			Assert.notNull(documentContext, "DocumentContext is required");
			Assert.notNull(assertDecorator, "`assertDecorator` is required");
			this.documentContext = documentContext;
		}

		private static List<GraphQLError> initErrors(DocumentContext documentContext) {
			try {
				return new ArrayList<>(documentContext.read(
						ERRORS_PATH, new TypeRef<List<TestGraphQLError>>() {}));
			}
			catch (PathNotFoundException ex) {
				return Collections.emptyList();
			}
		}


		@Override
		public ResponseSpec errorsSatisfy(Consumer<List<GraphQLError>> errorConsumer) {
			consumeErrors(errorConsumer);
			return this;
		}

		@Override
		public PathSpec path(String path) {
			assertErrorsEmptyOrConsumed();
			return new DefaultPathSpec(path, this.documentContext, getAssertDecorator());
		}
	}


	/**
	 * {@link PathSpec} implementation.
	 */
	private static class DefaultPathSpec implements PathSpec {

		private final String inputPath;

		private final DocumentContext documentContext;

		private final Consumer<Runnable> assertDecorator;

		private final JsonPath jsonPath;

		private final JsonPathExpectationsHelper pathHelper;

		private final String content;


		DefaultPathSpec(String path, DocumentContext documentContext, Consumer<Runnable> assertDecorator) {
			Assert.notNull(path, "`path` is required");
			this.inputPath = path;
			this.documentContext = documentContext;
			this.assertDecorator = assertDecorator;
			this.jsonPath = initPath(path);
			this.pathHelper = new JsonPathExpectationsHelper(this.jsonPath.getPath());
			this.content = documentContext.jsonString();
		}

		private static JsonPath initPath(String path) {
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
			return new DefaultPathSpec(path, this.documentContext, this.assertDecorator);
		}

		@Override
		public PathSpec pathExists() {
			this.assertDecorator.accept(() -> this.pathHelper.hasJsonPath(this.content));
			return this;
		}

		@Override
		public PathSpec pathDoesNotExist() {
			this.assertDecorator.accept(() -> this.pathHelper.doesNotHaveJsonPath(this.content));
			return this;
		}

		@Override
		public PathSpec valueExists() {
			this.assertDecorator.accept(() -> this.pathHelper.exists(this.content));
			return this;
		}

		@Override
		public PathSpec valueDoesNotExist() {
			this.assertDecorator.accept(() -> this.pathHelper.doesNotExist(this.content));
			return this;
		}

		@Override
		public PathSpec valueIsEmpty() {
			this.assertDecorator.accept(() -> {
				try {
					this.pathHelper.assertValueIsEmpty(this.content);
				}
				catch (AssertionError ex) {
					// ignore
				}
			});
			return this;
		}

		@Override
		public PathSpec valueIsNotEmpty() {
			this.assertDecorator.accept(() -> this.pathHelper.assertValueIsNotEmpty(this.content));
			return this;
		}

		@Override
		public <D> EntitySpec<D, ?> entity(Class<D> entityType) {
			D entity = this.documentContext.read(this.jsonPath, new TypeRefAdapter<>(entityType));
			return new DefaultEntitySpec<>(entity, this.documentContext, assertDecorator, this.inputPath);
		}

		@Override
		public <D> EntitySpec<D, ?> entity(ParameterizedTypeReference<D> entityType) {
			D entity = this.documentContext.read(this.jsonPath, new TypeRefAdapter<>(entityType));
			return new DefaultEntitySpec<>(entity, this.documentContext, assertDecorator, this.inputPath);
		}

		@Override
		public <D> ListEntitySpec<D> entityList(Class<D> elementType) {
			List<D> entity = this.documentContext.read(this.jsonPath, new TypeRefAdapter<>(List.class, elementType));
			return new DefaultListEntitySpec<>(entity, this.documentContext, assertDecorator, this.inputPath);
		}

		@Override
		public <D> ListEntitySpec<D> entityList(ParameterizedTypeReference<D> elementType) {
			List<D> entity = this.documentContext.read(this.jsonPath, new TypeRefAdapter<>(List.class, elementType));
			return new DefaultListEntitySpec<>(entity, this.documentContext, assertDecorator, this.inputPath);
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
			this.assertDecorator.accept(() -> {
				String actual;
				try {
					JsonProvider jsonProvider = this.documentContext.configuration().jsonProvider();
					Object content = this.documentContext.read(this.jsonPath);
					actual = jsonProvider.toJson(content);
				}
				catch (Exception ex) {
					throw new AssertionError("JSON parsing error", ex);
				}
				try {
					new JsonExpectationsHelper().assertJsonEqual(expected, actual, strict);
				}
				catch (AssertionError ex) {
					throw new AssertionError(ex.getMessage() + "\n\n" +
							"Expected JSON content:\n'" + expected + "'\n\n" +
							"Actual JSON content:\n'" + actual + "'\n\n" +
							"Input path: '" + this.inputPath + "'\n", ex);
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

		private final DocumentContext documentContext;

		private final Consumer<Runnable> assertDecorator;

		private final String inputPath;

		DefaultEntitySpec(D entity, DocumentContext context, Consumer<Runnable> decorator, String path) {
			this.entity = entity;
			this.documentContext = context;
			this.assertDecorator = decorator;
			this.inputPath = path;
		}

		protected D getEntity() {
			return this.entity;
		}

		protected String getInputPath() {
			return this.inputPath;
		}

		protected Consumer<Runnable> getAssertDecorator() {
			return this.assertDecorator;
		}

		@Override
		public PathSpec path(String path) {
			return new DefaultPathSpec(path, this.documentContext, this.assertDecorator);
		}

		@Override
		public <T extends S> T isEqualTo(Object expected) {
			this.assertDecorator.accept(() -> AssertionErrors.assertEquals(this.inputPath, expected, this.entity));
			return self();
		}

		@Override
		public <T extends S> T isNotEqualTo(Object other) {
			this.assertDecorator.accept(() -> AssertionErrors.assertNotEquals(this.inputPath, other, this.entity));
			return self();
		}

		@Override
		public <T extends S> T isSameAs(Object expected) {
			this.assertDecorator.accept(() -> AssertionErrors.assertTrue(this.inputPath, expected == this.entity));
			return self();
		}

		@Override
		public <T extends S> T isNotSameAs(Object other) {
			this.assertDecorator.accept(() -> AssertionErrors.assertTrue(this.inputPath, other != this.entity));
			return self();
		}

		@Override
		public <T extends S> T matches(Predicate<D> predicate) {
			this.assertDecorator.accept(() -> AssertionErrors.assertTrue(this.inputPath, predicate.test(this.entity)));
			return self();
		}

		@Override
		public <T extends S> T satisfies(Consumer<D> consumer) {
			this.assertDecorator.accept(() -> consumer.accept(this.entity));
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

		DefaultListEntitySpec(List<E> entity, DocumentContext context, Consumer<Runnable> decorator, String path) {
			super(entity, context, decorator, path);
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListEntitySpec<E> contains(E... elements) {
			getAssertDecorator().accept(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' does not contain " + expected,
						(getEntity() != null && getEntity().containsAll(expected)));
			});
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListEntitySpec<E> doesNotContain(E... elements) {
			getAssertDecorator().accept(() -> {
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
			getAssertDecorator().accept(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should have contained exactly " + expected,
						(getEntity() != null && getEntity().containsAll(expected)));
			});
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSize(int size) {
			getAssertDecorator().accept(() -> {
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should have size " + size,
						(getEntity() != null && getEntity().size() == size));
			});
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSizeLessThan(int boundary) {
			getAssertDecorator().accept(() -> {
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should have size less than " + boundary,
						(getEntity() != null && getEntity().size() < boundary));
			});
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSizeGreaterThan(int boundary) {
			getAssertDecorator().accept(() -> {
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should have size greater than " + boundary,
						(getEntity() != null && getEntity().size() > boundary));
			});
			return this;
		}
	}


	/**
	 * {@link SubscriptionSpec} implementation that operates on a
	 * {@link Publisher} of {@link ExecutionResult}.
	 */
	private static class DefaultSubscriptionSpec extends ResponseSpecSupport implements SubscriptionSpec {

		private final Publisher<ExecutionResult> publisher;

		private final Configuration jsonPathConfig;

		<T> DefaultSubscriptionSpec(
				Publisher<ExecutionResult> publisher, List<GraphQLError> errors, Configuration jsonPathConfig,
				Consumer<Runnable> assertDecorator) {

			super(errors, assertDecorator);
			this.publisher = publisher;
			this.jsonPathConfig = jsonPathConfig;
		}

		@Override
		public SubscriptionSpec errorsSatisfy(Consumer<List<GraphQLError>> errorConsumer) {
			consumeErrors(errorConsumer);
			return this;
		}

		@Override
		public Flux<ResponseSpec> toFlux() {
			return Flux.from(this.publisher).map(result -> {
				DocumentContext context = JsonPath.parse(result.toSpecification(), this.jsonPathConfig);
				return new DefaultResponseSpec(context, getAssertDecorator());
			});
		}
	}


	private static class Jackson2Configuration {

		static Configuration create() {
			return Configuration.builder()
					.jsonProvider(new JacksonJsonProvider())
					.mappingProvider(new JacksonMappingProvider())
					.build();
		}
	}

}
