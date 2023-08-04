/*
 * Copyright 2002-2023 the original author or authors.
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

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.support.DefaultGraphQlRequest;
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
 * <p>This class is final and works with any transport.
 *
 * @author Rossen Stoyanchev
 */
final class DefaultGraphQlTester implements GraphQlTester {

	private final GraphQlTransport transport;

	@Nullable
	private final Predicate<ResponseError> errorFilter;

	private final Configuration jsonPathConfig;

	private final DocumentSource documentSource;

	private final Duration responseTimeout;


	/**
	 * Package private constructor for use from {@link AbstractGraphQlTesterBuilder}.
	 */
	DefaultGraphQlTester(
			GraphQlTransport transport, @Nullable Predicate<ResponseError> errorFilter,
			Configuration jsonPathConfig, DocumentSource documentSource, Duration timeout) {

		Assert.notNull(transport, "GraphQlTransport is required");
		Assert.notNull(jsonPathConfig, "JSONPath Configuration is required");
		Assert.notNull(documentSource, "DocumentSource is required");

		this.transport = transport;
		this.errorFilter = errorFilter;
		this.jsonPathConfig = jsonPathConfig;
		this.documentSource = documentSource;
		this.responseTimeout = timeout;
	}


	@Override
	public Request<?> document(String document) {
		return new DefaultRequest(document);
	}

	@Override
	public Request<?> documentName(String documentName) {
		String document = this.documentSource.getDocument(documentName).block(this.responseTimeout);
		Assert.notNull(document, "DocumentSource completed empty");
		return document(document);
	}

	/**
	 * The default tester is unaware of transport details, and cannot implement
	 * mutate directly. It should be wrapped from transport aware extensions via
	 * {@link AbstractDelegatingGraphQlTester} that also implement mutate.
	 */
	@Override
	public Builder<?> mutate() {
		throw new UnsupportedOperationException();
	}


	/**
	 * {@link Request} that gathers the document, operationName, and variables.
	 */
	private final class DefaultRequest implements Request<DefaultRequest> {

		private final String document;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private final Map<String, Object> extensions = new LinkedHashMap<>();

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

		@Override
		public DefaultRequest extension(String name, @Nullable Object value) {
			this.extensions.put(name, value);
			return this;
		}

		@SuppressWarnings("ConstantConditions")
		@Override
		public Response execute() {
			return transport.execute(request()).map(response -> mapResponse(response, request())).block(responseTimeout);
		}

		@Override
		public void executeAndVerify() {
			execute().path("$.errors").pathDoesNotExist();
		}

		@Override
		public Subscription executeSubscription() {
			return () -> transport.executeSubscription(request()).map(result -> mapResponse(result, request()));
		}

		private GraphQlRequest request() {
			return new DefaultGraphQlRequest(this.document, this.operationName, this.variables, this.extensions);
		}

		private DefaultResponse mapResponse(GraphQlResponse response, GraphQlRequest request) {
			return new DefaultResponse(response, errorFilter, assertDecorator(request), jsonPathConfig);
		}

		private Consumer<Runnable> assertDecorator(GraphQlRequest request) {
			return assertion -> {
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
	private static final class ResponseDelegate {

		private final DocumentContext jsonDoc;

		private final Supplier<String> jsonContent;

		private final List<ResponseError> errors;

		private final List<ResponseError> unexpectedErrors;

		private final Consumer<Runnable> assertDecorator;


		private ResponseDelegate(
				GraphQlResponse response, @Nullable Predicate<ResponseError> errorFilter,
				Consumer<Runnable> assertDecorator, Configuration jsonPathConfig) {

			this.jsonDoc = JsonPath.parse(response.toMap(), jsonPathConfig);
			this.jsonContent = this.jsonDoc::jsonString;
			this.errors = response.getErrors();
			this.unexpectedErrors = new ArrayList<>(this.errors);
			this.assertDecorator = assertDecorator;

			if (errorFilter != null) {
				filterErrors(errorFilter);
			}
		}

		String jsonContent() {
			return this.jsonContent.get();
		}

		String jsonContent(JsonPath jsonPath) {
			try {
				Object content = this.jsonDoc.read(jsonPath);
				return this.jsonDoc.configuration().jsonProvider().toJson(content);
			}
			catch (Exception ex) {
				throw new AssertionError("JSON parsing error", ex);
			}
		}

		<T> T read(JsonPath jsonPath, TypeRef<T> typeRef) {
			return this.jsonDoc.read(jsonPath, typeRef);
		}

		void doAssert(Runnable task) {
			this.assertDecorator.accept(task);
		}

		boolean filterErrors(Predicate<ResponseError> predicate) {
			boolean filtered = false;
			for (ResponseError error : this.errors) {
				if (predicate.test(error)) {
					this.unexpectedErrors.remove(error);
					filtered = true;
				}
			}
			return filtered;
		}

		void expectErrors(Predicate<ResponseError> predicate) {
			boolean filtered = filterErrors(predicate);
			this.assertDecorator.accept(() -> AssertionErrors.assertTrue("No matching errors.", filtered));
		}

		void consumeErrors(Consumer<List<ResponseError>> consumer) {
			filterErrors(error -> true);
			consumer.accept(this.errors);
		}

		void verifyErrors() {
			this.assertDecorator.accept(() ->
					AssertionErrors.assertTrue(
							"Response has " + this.unexpectedErrors.size() + " unexpected error(s) " +
									"of " + this.errors.size() + " total. " +
									"If expected, please filter them out: " + this.unexpectedErrors,
							CollectionUtils.isEmpty(this.unexpectedErrors)));
		}

	}


	/**
	 * Default {@link GraphQlTester.Response} implementation.
	 */
	private static final class DefaultResponse implements Response, Errors {

		private final ResponseDelegate delegate;

		private DefaultResponse(
				GraphQlResponse response, @Nullable Predicate<ResponseError> errorFilter,
				Consumer<Runnable> assertDecorator, Configuration jsonPathConfig) {

			this.delegate = new ResponseDelegate(response, errorFilter, assertDecorator, jsonPathConfig);
		}

		@Override
		public Path path(String path) {
			this.delegate.verifyErrors();
			return DefaultPath.forPath(null, path, this.delegate);
		}

		@Override
		public Path path(String path, Consumer<Path> pathConsumer) {
			return DefaultPath.forNestedPath(null, path, this.delegate, pathConsumer);
		}

		@Override
		public Errors errors() {
			return this;
		}

		@Override
		public Errors filter(Predicate<ResponseError> predicate) {
			this.delegate.filterErrors(predicate);
			return this;
		}

		@Override
		public Errors expect(Predicate<ResponseError> predicate) {
			this.delegate.expectErrors(predicate);
			return this;
		}

		@Override
		public Traversable verify() {
			this.delegate.verifyErrors();
			return this;
		}

		@Override
		public Traversable satisfy(Consumer<List<ResponseError>> consumer) {
			this.delegate.consumeErrors(consumer);
			return this;
		}

	}

	/**
	 * Default {@link GraphQlTester.Path} implementation.
	 */
	private static final class DefaultPath implements Path {

		@Nullable
		private final String basePath;

		private final String path;

		private final ResponseDelegate delegate;

		private final JsonPath jsonPath;

		private final JsonPathExpectationsHelper pathHelper;

		private DefaultPath(@Nullable String basePath, String path, ResponseDelegate delegate) {
			Assert.notNull(path, "`path` is required");
			Assert.notNull(delegate, "ResponseContainer is required");

			this.basePath = basePath;
			this.path = path;
			this.delegate = delegate;

			String fullPath = initDataJsonPath(this.path);
			this.jsonPath = JsonPath.compile(fullPath);
			this.pathHelper = new JsonPathExpectationsHelper(fullPath);
		}

		private static String initDataJsonPath(String path) {
			if (!StringUtils.hasText(path)) {
				path = "$.data";
			}
			else if (!path.startsWith("$") && !path.startsWith("data.")) {
				path = "$.data." + path;
			}
			return path;
		}

		@Override
		public Path path(String path) {
			return forPath(this.basePath, path, this.delegate);
		}

		@Override
		public Path path(String path, Consumer<Path> pathConsumer) {
			return forNestedPath(this.basePath, path, this.delegate, pathConsumer);
		}

		@Override
		public Path hasValue() {
			this.delegate.doAssert(() -> this.pathHelper.exists(this.delegate.jsonContent()));
			return this;
		}

		@Override
		public Path valueIsNull() {
			Assert.isTrue(this.jsonPath.isDefinite(), "isNull applies only to JSONPath targeting a single value");
			this.delegate.doAssert(() -> {
				Object value = this.pathHelper.evaluateJsonPath(this.delegate.jsonContent());
				AssertionErrors.assertNull(
						"Expected null value at JSON path \"" + path + "\" but found " + value, value);
			});
			return this;
		}

		@Override
		public Path pathDoesNotExist() {
			this.delegate.doAssert(() -> this.pathHelper.doesNotHaveJsonPath(this.delegate.jsonContent()));
			return this;
		}

		@Override
		public <D> Entity<D, ?> entity(Class<D> entityType) {
			return new DefaultEntity<>(new TypeRefAdapter<>(entityType));
		}

		@Override
		public <D> Entity<D, ?> entity(ParameterizedTypeReference<D> entityType) {
			return new DefaultEntity<>(new TypeRefAdapter<>(entityType));
		}

		@Override
		public <D> EntityList<D> entityList(Class<D> elementType) {
			return new DefaultEntityList<>(new TypeRefAdapter<>(List.class, elementType));
		}

		@Override
		public <D> EntityList<D> entityList(ParameterizedTypeReference<D> elementType) {
			return new DefaultEntityList<>(new TypeRefAdapter<>(List.class, elementType));
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
			this.delegate.doAssert(() -> {
				String actual = this.delegate.jsonContent(this.jsonPath);
				try {
					new JsonExpectationsHelper().assertJsonEqual(expected, actual, strict);
				}
				catch (AssertionError ex) {
					throw new AssertionError(ex.getMessage() + "\n\n" + "Expected JSON content:\n'" + expected + "'\n\n"
							+ "Actual JSON content:\n'" + actual + "'\n\n" + "Input path: '" + this.path + "'\n",
							ex);
				}
				catch (Exception ex) {
					throw new AssertionError("JSON parsing error", ex);
				}
			});
		}

		static Path forPath(@Nullable String basePath, String path, ResponseDelegate delegate) {
			String pathToUse = joinPaths(basePath, path);
			return new DefaultPath(basePath, pathToUse, delegate);
		}

		static Path forNestedPath(@Nullable String basePath, String path, ResponseDelegate delegate, Consumer<Path> consumer) {
			String pathToUse = joinPaths(basePath, path);
			consumer.accept(new DefaultPath(pathToUse, pathToUse, delegate));
			return new DefaultPath(basePath, path, delegate);
		}

		private static String joinPaths(@Nullable String basePath, String path) {
			return basePath != null ? basePath + "." + path : path;
		}


		/**
		 * Default {@link GraphQlTester.Entity} implementation.
		 */
		private class DefaultEntity<D, S extends Entity<D, S>> implements Entity<D, S> {

			private final D entity;

			protected DefaultEntity(TypeRefAdapter<D> typeAdapter) {
				this.entity = delegate.read(jsonPath, typeAdapter);
			}

			protected D getEntity() {
				return this.entity;
			}

			protected void doAssert(Runnable task) {
				delegate.doAssert(task);
			}

			protected String getPath() {
				return path;
			}

			@Override
			public Path path(String path) {
				return forPath(basePath, path, delegate);
			}

			@Override
			public Path path(String path, Consumer<Path> pathConsumer) {
				return forNestedPath(basePath, path, delegate, pathConsumer);
			}

			@Override
			public <T extends S> T isEqualTo(Object expected) {
				delegate.doAssert(() -> AssertionErrors.assertEquals(path, expected, this.entity));
				return self();
			}

			@Override
			public <T extends S> T isNotEqualTo(Object other) {
				delegate.doAssert(() -> AssertionErrors.assertNotEquals(path, other, this.entity));
				return self();
			}

			@Override
			public <T extends S> T isSameAs(Object expected) {
				delegate.doAssert(() -> AssertionErrors.assertTrue(path, expected == this.entity));
				return self();
			}

			@Override
			public <T extends S> T isNotSameAs(Object other) {
				delegate.doAssert(() -> AssertionErrors.assertTrue(path, other != this.entity));
				return self();
			}

			@Override
			public <T extends S> T matches(Predicate<D> predicate) {
				delegate
						.doAssert(() -> AssertionErrors.assertTrue(path, predicate.test(this.entity)));
				return self();
			}

			@Override
			public <T extends S> T satisfies(Consumer<D> consumer) {
				delegate.doAssert(() -> consumer.accept(this.entity));
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
		@SuppressWarnings("SlowListContainsAll")
		private final class DefaultEntityList<E>
				extends DefaultEntity<List<E>, EntityList<E>> implements EntityList<E> {

			public DefaultEntityList(TypeRefAdapter<List<E>> typeAdapter) {
				super(typeAdapter);
			}

			@Override
			@SuppressWarnings("unchecked")
			public EntityList<E> contains(E... values) {
				doAssert(() -> {
					List<E> expected = Arrays.asList(values);
					AssertionErrors.assertTrue(
							"Expecting list " + getEntity() + " at path '" + getPath() + "' to contain " + expected,
							getEntity().containsAll(expected));
				});
				return this;
			}

			@Override
			@SuppressWarnings("unchecked")
			public EntityList<E> doesNotContain(E... values) {
				doAssert(() -> {
					List<E> expected = Arrays.asList(values);
					AssertionErrors.assertTrue(
							"Expecting list " + getEntity() + " at path '" + getPath() + "' to not contain " + expected,
							!getEntity().containsAll(expected));
				});
				return this;
			}

			@Override
			@SuppressWarnings("unchecked")
			public EntityList<E> containsExactly(E... values) {
				doAssert(() -> {
					List<E> expected = Arrays.asList(values);
					AssertionErrors.assertTrue(
							"Expecting list " + getEntity() + " at path '" + getPath() + "' to contain exactly " + expected,
							getEntity().equals(expected));
				});
				return this;
			}

			@Override
			public EntityList<E> hasSize(int size) {
				doAssert(() -> AssertionErrors.assertTrue(
						"Expecting list " + getEntity() + " at path '" + getPath() + "' to have size == " + size,
						getEntity().size() == size));
				return this;
			}

			@Override
			public EntityList<E> hasSizeLessThan(int size) {
				doAssert(() -> AssertionErrors.assertTrue(
						"Expecting list " + getEntity() + " at path '" + getPath() + "' to have size < " + size,
						getEntity().size() < size));
				return this;
			}

			@Override
			public EntityList<E> hasSizeGreaterThan(int size) {
				doAssert(() -> AssertionErrors.assertTrue(
						"Expecting list " + getEntity() + " at path '" + getPath() + "' to have size > " + size,
						getEntity().size() > size));
				return this;
			}
		}


		/**
		 * Adapt JSONPath {@link TypeRef} to {@link ParameterizedTypeReference}.
		 */
		private static final class TypeRefAdapter<T> extends TypeRef<T> {

			private final Type type;

			TypeRefAdapter(Class<T> clazz) {
				this.type = clazz;
			}

			TypeRefAdapter(ParameterizedTypeReference<T> typeReference) {
				this.type = typeReference.getType();
			}

			TypeRefAdapter(Class<?> clazz, Class<?> generic) {
				this.type = ResolvableType.forClassWithGenerics(clazz, generic).getType();
			}

			TypeRefAdapter(Class<?> clazz, ParameterizedTypeReference<?> generic) {
				this.type = ResolvableType.forClassWithGenerics(clazz, ResolvableType.forType(generic)).getType();
			}

			@Override
			public Type getType() {
				return this.type;
			}

		}

	}

}
