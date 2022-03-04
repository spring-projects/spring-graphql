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
import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.lang.Nullable;
import org.springframework.test.util.AssertionErrors;
import org.springframework.test.util.JsonExpectationsHelper;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default {@link GraphQlTester} implementation with the logic to initialize
 * requests and handle responses. It is transport agnostic and depends on a
 * {@link GraphQlTransport} to execute requests with.
 *
 * <p>This class is final but works with any transport.
 *
 * @author Rossen Stoyanchev
 */
final class DefaultGraphQlTester implements GraphQlTester {

	private final GraphQlTransport transport;

	@Nullable
	private final Predicate<GraphQLError> errorFilter;

	private final Configuration jsonPathConfig;

	private final DocumentSource documentSource;

	private final Duration responseTimeout;

	private final Consumer<GraphQlTester.Builder<?>> builderInitializer;


	/**
	 * Package private constructor for use from {@link AbstractGraphQlTesterBuilder}.
	 */
	DefaultGraphQlTester(
			GraphQlTransport transport, @Nullable Predicate<GraphQLError> errorFilter,
			Configuration jsonPathConfig, DocumentSource documentSource, Duration timeout,
			Consumer<GraphQlTester.Builder<?>> builderInitializer) {

		this.transport = transport;
		this.errorFilter = errorFilter;
		this.jsonPathConfig = jsonPathConfig;
		this.documentSource = documentSource;
		this.responseTimeout = timeout;
		this.builderInitializer = builderInitializer;
	}


	@Override
	public Request<?> document(String document) {
		return new DefaultRequest(document);
	}

	@Override
	public Request<?> documentName(String documentName) {
		String document = this.documentSource.getDocument(documentName).block(this.responseTimeout);
		Assert.notNull(document, "Expected document content or an error");
		return document(document);
	}

	@Override
	public Builder mutate() {
		Builder builder = new Builder(this.transport);
		this.builderInitializer.accept(builder);
		return builder;
	}


	/**
	 * Default {@link GraphQlTester.Builder} with a given transport.
	 */
	static final class Builder extends AbstractGraphQlTesterBuilder<Builder> {

		private final GraphQlTransport transport;

		Builder(GraphQlTransport transport) {
			this.transport = transport;
		}

		@Override
		public GraphQlTester build() {
			return super.buildGraphQlTester(this.transport);
		}

	}


	/**
	 * {@link Request} that gathers the document, operationName, and variables.
	 */
	private final class DefaultRequest implements Request<DefaultRequest> {

		private final String document;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private DefaultRequest(String document) {
			Assert.notNull(document, "`document` is required");
			this.document = document;
		}

		@Override
		public DefaultRequest operationName(@Nullable String name) {
			this.operationName = name;
			return this;
		}

		@Override
		public DefaultRequest variable(String name, @Nullable Object value) {
			this.variables.put(name, value);
			return this;
		}

		@SuppressWarnings("ConstantConditions")
		@Override
		public Response execute() {
			GraphQlRequest request = createRequest();
			return transport.execute(request)
					.map(result -> createResponseSpec(result, assertDecorator(request)))
					.block(responseTimeout);
		}

		@Override
		public void executeAndVerify() {
			execute().path("$.errors").valueIsEmpty();
		}

		@Override
		public Subscription executeSubscription() {
			GraphQlRequest request = createRequest();
			return () -> transport.executeSubscription(request)
					.map(result -> createResponseSpec(result, assertDecorator(request)));
		}

		private GraphQlRequest createRequest() {
			return new GraphQlRequest(this.document, this.operationName, this.variables);
		}

		private Response createResponseSpec(
				ExecutionResult result, Consumer<Runnable> assertDecorator) {

			DocumentContext jsonDocument = JsonPath.parse(result.toSpecification(), jsonPathConfig);
			return new DefaultResponse(jsonDocument, errorFilter, assertDecorator);
		}

		private Consumer<Runnable> assertDecorator(GraphQlRequest request) {
			return (assertion) -> {
				try {
					assertion.run();
				}
				catch (AssertionError ex) {
					throw new AssertionError(ex.getMessage() + "\nRequest: " + request, ex);
				}
			};
		}

	}


	/**
	 * Container for GraphQL response data and errors along with convenience methods.
	 */
	private final static class ResponseContainer {

		private static final TypeRef<List<TestGraphQlError>> ERROR_LIST_TYPE = new TypeRef<List<TestGraphQlError>>() {};

		private static final JsonPath ERRORS_PATH = JsonPath.compile("$.errors");

		private static final Predicate<GraphQLError> MATCH_ALL_PREDICATE = (error) -> true;


		private final DocumentContext documentContext;

		private final String jsonContent;

		private final List<TestGraphQlError> errors;

		private final Consumer<Runnable> assertDecorator;


		private ResponseContainer(
				DocumentContext documentContext, @Nullable Predicate<GraphQLError> errorFilter,
				Consumer<Runnable> assertDecorator) {

			Assert.notNull(assertDecorator, "`assertDecorator` is required");
			this.documentContext = documentContext;
			this.jsonContent = this.documentContext.jsonString();
			this.errors = readErrors(documentContext);
			this.assertDecorator = assertDecorator;

			filterErrors(errorFilter);
		}

		private static List<TestGraphQlError> readErrors(DocumentContext documentContext) {
			Assert.notNull(documentContext, "DocumentContext is required");
			try {
				return documentContext.read(ERRORS_PATH, ERROR_LIST_TYPE);
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

		void doAssert(Runnable task) {
			this.assertDecorator.accept(task);
		}

		boolean filterErrors(@Nullable Predicate<GraphQLError> predicate) {
			boolean filtered = false;
			if (predicate != null) {
				for (TestGraphQlError error : this.errors) {
					filtered |= error.apply(predicate);
				}
			}
			return filtered;
		}

		void expectErrors(@Nullable Predicate<GraphQLError> predicate) {
			boolean filtered = filterErrors(predicate);
			this.assertDecorator.accept(() -> AssertionErrors.assertTrue("No matching errors.", filtered));
		}

		void consumeErrors(Consumer<List<GraphQLError>> consumer) {
			filterErrors(MATCH_ALL_PREDICATE);
			consumer.accept(new ArrayList<>(this.errors));
		}

		void verifyErrors() {
			List<TestGraphQlError> unexpected = this.errors.stream()
					.filter(error -> !error.isExpected())
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
	 * Default {@link GraphQlTester.Response} implementation.
	 */
	private static final class DefaultResponse implements Response, Errors {

		private final ResponseContainer responseContainer;

		private DefaultResponse(
				DocumentContext documentContext, @Nullable Predicate<GraphQLError> errorFilter,
				Consumer<Runnable> assertDecorator) {

			this.responseContainer = new ResponseContainer(documentContext, errorFilter, assertDecorator);
		}

		@Override
		public Path path(String path) {
			this.responseContainer.verifyErrors();
			return new DefaultPath(path, this.responseContainer);
		}

		@Override
		public Errors errors() {
			return this;
		}

		@Override
		public Errors filter(Predicate<GraphQLError> predicate) {
			this.responseContainer.filterErrors(predicate);
			return this;
		}

		@Override
		public Errors expect(Predicate<GraphQLError> predicate) {
			this.responseContainer.expectErrors(predicate);
			return this;
		}

		@Override
		public Traversable verify() {
			this.responseContainer.verifyErrors();
			return this;
		}

		@Override
		public Traversable satisfy(Consumer<List<GraphQLError>> consumer) {
			this.responseContainer.consumeErrors(consumer);
			return this;
		}

	}

	/**
	 * Default {@link GraphQlTester.Path} implementation.
	 */
	private static final class DefaultPath implements Path {

		private final String inputPath;

		private final ResponseContainer responseContainer;

		private final JsonPath jsonPath;

		private final JsonPathExpectationsHelper pathHelper;

		private DefaultPath(String path, ResponseContainer responseContainer) {
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
		public Path path(String path) {
			return new DefaultPath(path, this.responseContainer);
		}

		@Override
		public Path pathExists() {
			this.responseContainer.doAssert(() -> this.pathHelper.hasJsonPath(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public Path pathDoesNotExist() {
			this.responseContainer.doAssert(() -> this.pathHelper.doesNotHaveJsonPath(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public Path valueExists() {
			this.responseContainer.doAssert(() -> this.pathHelper.exists(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public Path valueDoesNotExist() {
			this.responseContainer.doAssert(() -> this.pathHelper.doesNotExist(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public Path valueIsEmpty() {
			this.responseContainer.doAssert(() -> this.pathHelper.assertValueIsEmpty(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public Path valueIsNotEmpty() {
			this.responseContainer.doAssert(() -> this.pathHelper.assertValueIsNotEmpty(this.responseContainer.jsonContent()));
			return this;
		}

		@Override
		public <D> Entity<D, ?> entity(Class<D> entityType) {
			D entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(entityType));
			return new DefaultEntity<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public <D> Entity<D, ?> entity(ParameterizedTypeReference<D> entityType) {
			D entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(entityType));
			return new DefaultEntity<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public <D> EntityList<D> entityList(Class<D> elementType) {
			List<D> entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(List.class, elementType));
			return new DefaultEntityList<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public <D> EntityList<D> entityList(ParameterizedTypeReference<D> elementType) {
			List<D> entity = this.responseContainer.read(this.jsonPath, new TypeRefAdapter<>(List.class, elementType));
			return new DefaultEntityList<>(entity, this.responseContainer, this.inputPath);
		}

		@Override
		public Path matchesJson(String expectedJson) {
			matchesJson(expectedJson, false);
			return this;
		}

		@Override
		public Path matchesJsonStrictly(String expectedJson) {
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
	 * Default {@link GraphQlTester.Entity} implementation.
	 */
	private static class DefaultEntity<D, S extends Entity<D, S>> implements Entity<D, S> {

		private final D entity;

		private final ResponseContainer responseContainer;

		private final String inputPath;

		protected DefaultEntity(D entity, ResponseContainer responseContainer, String path) {
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
		public Path path(String path) {
			return new DefaultPath(path, this.responseContainer);
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
	 * Default {@link EntityList} implementation.
	 */
	private static final class DefaultEntityList<E> extends DefaultEntity<List<E>, EntityList<E>>
			implements EntityList<E> {

		private DefaultEntityList(List<E> entity, ResponseContainer responseContainer, String path) {
			super(entity, responseContainer, path);
		}

		@Override
		@SuppressWarnings("unchecked")
		public EntityList<E> contains(E... elements) {
			doAssert(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue("List at path '" + getInputPath() + "' does not contain " + expected,
						getEntity().containsAll(expected));
			});
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public EntityList<E> doesNotContain(E... elements) {
			doAssert(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should not have contained " + expected,
						!getEntity().containsAll(expected));
			});
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public EntityList<E> containsExactly(E... elements) {
			doAssert(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue(
						"List at path '" + getInputPath() + "' should have contained exactly " + expected,
						getEntity().containsAll(expected));
			});
			return this;
		}

		@Override
		public EntityList<E> hasSize(int size) {
			doAssert(() -> AssertionErrors.assertTrue("List at path '" + getInputPath() + "' should have size " + size,
					getEntity().size() == size));
			return this;
		}

		@Override
		public EntityList<E> hasSizeLessThan(int boundary) {
			doAssert(() -> AssertionErrors.assertTrue(
					"List at path '" + getInputPath() + "' should have size less than " + boundary,
					getEntity().size() < boundary));
			return this;
		}

		@Override
		public EntityList<E> hasSizeGreaterThan(int boundary) {
			doAssert(() -> AssertionErrors.assertTrue(
					"List at path '" + getInputPath() + "' should have size greater than " + boundary,
					getEntity().size() > boundary));
			return this;
		}
	}

}
