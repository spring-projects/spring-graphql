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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import graphql.GraphQLError;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;
import org.springframework.test.util.AssertionErrors;
import org.springframework.test.util.JsonExpectationsHelper;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link GraphQlTester}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultGraphQlTester implements GraphQlTester {

	private final RequestStrategy requestStrategy;

	private final Function<String, String> operationNameResolver;


	DefaultGraphQlTester(RequestStrategy requestStrategy, Function<String, String> operationNameResolver) {
		Assert.notNull(requestStrategy, "RequestStrategy is required.");
		Assert.notNull(operationNameResolver, "'operationNameResolver' is required.");
		this.requestStrategy = requestStrategy;
		this.operationNameResolver = operationNameResolver;
	}


	@Override
	public RequestSpec<?> query(String query) {
		return new DefaultRequestSpec(this.requestStrategy, query);
	}

	@Override
	public RequestSpec<?> operationName(String operationName) {
		return query(this.operationNameResolver.apply(operationName));
	}


	/**
	 * Factory for {@link GraphQlTester.ResponseSpec}, for use from
	 * {@link RequestStrategy} implementations.
	 *
	 * @param documentContext the parsed response content
	 * @param errorFilter a globally defined filter for expected errors (to be ignored)
	 * @param assertDecorator decorator to apply around assertions, e.g. to add extra
	 */
	static GraphQlTester.ResponseSpec createResponseSpec(
			DocumentContext documentContext, @Nullable Predicate<GraphQLError> errorFilter,
			Consumer<Runnable> assertDecorator) {

		return new DefaultResponseSpec(documentContext, errorFilter, assertDecorator);
	}


	/**
	 * {@link RequestSpec} that collects the query, operationName, and variables.
	 */
	private static final class DefaultRequestSpec
			extends GraphQlTesterRequestSpecSupport implements RequestSpec<DefaultRequestSpec> {

		private final RequestStrategy requestStrategy;

		private DefaultRequestSpec(RequestStrategy requestStrategy, String query) {
			super(query);
			Assert.notNull(requestStrategy, "RequestStrategy is required");
			this.requestStrategy = requestStrategy;
		}

		@Override
		public DefaultRequestSpec operationName(@Nullable String name) {
			setOperationName(name);
			return this;
		}

		@Override
		public DefaultRequestSpec variable(String name, @Nullable Object value) {
			addVariable(name, value);
			return this;
		}

		@Override
		public DefaultRequestSpec locale(Locale locale) {
			setLocale(locale);
			return this;
		}

		@Override
		public ResponseSpec execute() {
			return this.requestStrategy.execute(createRequestInput());
		}

		@Override
		public void executeAndVerify() {
			verify(execute());
		}

		@Override
		public SubscriptionSpec executeSubscription() {
			return this.requestStrategy.executeSubscription(createRequestInput());
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
	 * {@link ResponseSpec} that operates on the response from a GraphQL HTTP request.
	 */
	private static final class DefaultResponseSpec implements ResponseSpec, ErrorSpec {

		private final ResponseContainer responseContainer;

		private DefaultResponseSpec(
				DocumentContext documentContext, @Nullable Predicate<GraphQLError> errorFilter,
				Consumer<Runnable> assertDecorator) {

			this.responseContainer = new ResponseContainer(documentContext, errorFilter, assertDecorator);
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
		public ErrorSpec expect(Predicate<GraphQLError> predicate) {
			this.responseContainer.expectErrors(predicate);
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
	private static final class DefaultPathSpec implements PathSpec {

		private final String inputPath;

		private final ResponseContainer responseContainer;

		private final JsonPath jsonPath;

		private final JsonPathExpectationsHelper pathHelper;

		private DefaultPathSpec(String path, ResponseContainer responseContainer) {
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

		protected DefaultEntitySpec(D entity, ResponseContainer responseContainer, String path) {
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
	private static final class DefaultListEntitySpec<E> extends DefaultEntitySpec<List<E>, ListEntitySpec<E>>
			implements ListEntitySpec<E> {

		private DefaultListEntitySpec(List<E> entity, ResponseContainer responseContainer, String path) {
			super(entity, responseContainer, path);
		}

		@Override
		@SuppressWarnings("unchecked")
		public ListEntitySpec<E> contains(E... elements) {
			doAssert(() -> {
				List<E> expected = Arrays.asList(elements);
				AssertionErrors.assertTrue("List at path '" + getInputPath() + "' does not contain " + expected,
						getEntity().containsAll(expected));
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
						!getEntity().containsAll(expected));
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
						getEntity().containsAll(expected));
			});
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSize(int size) {
			doAssert(() -> AssertionErrors.assertTrue("List at path '" + getInputPath() + "' should have size " + size,
					getEntity().size() == size));
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSizeLessThan(int boundary) {
			doAssert(() -> AssertionErrors.assertTrue(
					"List at path '" + getInputPath() + "' should have size less than " + boundary,
					getEntity().size() < boundary));
			return this;
		}

		@Override
		public ListEntitySpec<E> hasSizeGreaterThan(int boundary) {
			doAssert(() -> AssertionErrors.assertTrue(
					"List at path '" + getInputPath() + "' should have size greater than " + boundary,
					getEntity().size() > boundary));
			return this;
		}
	}

}
