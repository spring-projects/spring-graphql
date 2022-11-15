/*
 * Copyright 2020-2022 the original author or authors.
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

package org.springframework.graphql.data.method.annotation.support;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingFieldSelectionSet;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.aop.SpringProxy;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.DecoratingProxy;
import org.springframework.data.projection.TargetAware;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.LocalContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SchemaMappingBeanFactoryInitializationAotProcessor}.
 *
 * @author Brian Clozel
 */
class SchemaMappingBeanFactoryInitializationAotProcessorTests {

	private GenerationContext generationContext = new TestGenerationContext();

	private SchemaMappingBeanFactoryInitializationAotProcessor processor = new SchemaMappingBeanFactoryInitializationAotProcessor();

	@Test
	void processorIsRegisteredInAotFactories() {
		assertThat(AotServices.factories(getClass().getClassLoader()).load(BeanFactoryInitializationAotProcessor.class))
				.anyMatch(SchemaMappingBeanFactoryInitializationAotProcessor.class::isInstance);
	}

	@Nested
	class ArgumentTests {

		@Test
		void registerBindingReflectionOnReturnType() {
			processController(ReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Book.class);
		}

		@Test
		void registerBindingReflectionOnInput() {
			processController(InputController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Book.class, BookInput.class);
		}

		@Test
		void registerBindingReflectionOnArgumentCollection() {
			processController(ArgumentCollectionController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Book.class);
		}

		@Test
		void registerBindingReflectionOnArgumentValue() {
			processController(ArgumentValueController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Book.class, BookInput.class);
			assertThatHintsAreNotRegisteredForTypes(ArgumentValue.class);
		}

		@Test
		void registerBindingReflectionOnDataLoaderArgument() {
			processController(DataLoaderController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Author.class);
			assertThatHintsAreNotRegisteredForTypes(DataLoader.class);
		}

		@Test
		void registerBindingReflectionOnAsyncReturnType() {
			processController(AsyncReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Author.class);
		}


		@Controller
		static class ReturnTypeController {
			@QueryMapping
			public Book bookById(@Argument Long id) {
				return null;
			}

		}

		@Controller
		static class InputController {
			@MutationMapping
			public Book addBook(@Argument BookInput bookInput) {
				return null;
			}

		}

		@Controller
		static class ArgumentCollectionController {
			@MutationMapping
			public void addBooks(@Argument List<Book> books) {
			}

		}

		@Controller
		static class ArgumentValueController {
			@MutationMapping
			public Book addBook(ArgumentValue<BookInput> bookInput) {
				return null;
			}

		}

		@Controller
		static class DataLoaderController {
			@SchemaMapping
			public void authorWithLoader(DataLoader<Long, Author> loader) {
			}

		}

		@Controller
		static class AsyncReturnTypeController {
			@SchemaMapping
			public CompletableFuture<Author> author(Long bookId) {
				return null;
			}

		}

		static class BookInput {

			String name;

			Long authorId;

			public String getName() {
				return this.name;
			}

			public void setName(String name) {
				this.name = name;
			}

			public Long getAuthorId() {
				return this.authorId;
			}

			public void setAuthorId(Long authorId) {
				this.authorId = authorId;
			}
		}

	}

	@Nested
	class BatchMappingTests {

		@Test
		void registerBindingReflectionOnFluxReturnType() {
			processController(FluxReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Author.class);
		}

		@Test
		void registerBindingReflectionOnMonoMapReturnType() {
			processController(MonoMapReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Book.class, Author.class);
		}

		@Test
		void registerBindingReflectionOnMapReturnType() {
			processController(MapReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Book.class, Author.class);
		}

		@Test
		void registerBindingReflectionOnCollectionReturnType() {
			processController(CollectionReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Author.class);
		}

		@Test
		void registerBindingReflectionOnCallableCollectionReturnType() {
			processController(CallableCollectionReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Author.class);
		}

		@Test
		void registerBindingReflectionOnCallableMapReturnType() {
			processController(CallableMapReturnTypeController.class);
			assertThatHintsForJavaBeanBindingRegisteredForTypes(Book.class, Author.class);
		}

		@Controller
		static class FluxReturnTypeController {
			@BatchMapping
			public Flux<Author> batchMappingFlux() {
				return null;
			}
		}

		@Controller
		static class MonoMapReturnTypeController {
			@BatchMapping
			public Mono<Map<Book, Author>> batchMappingMono() {
				return null;
			}
		}

		@Controller
		static class MapReturnTypeController {
			@BatchMapping
			public Map<Book, Author> batchMappingMap() {
				return null;
			}
		}

		@Controller
		static class CollectionReturnTypeController {

			@BatchMapping
			public Collection<Author> batchMappingCollection() {
				return null;
			}
		}

		@Controller
		static class CallableCollectionReturnTypeController {
			@BatchMapping
			public Callable<Collection<Author>> batchMappingCallableCollection() {
				return null;
			}
		}

		@Controller
		static class CallableMapReturnTypeController {
			@BatchMapping
			public Callable<Map<Book, Author>> batchMappingCallableMap() {
				return null;
			}
		}

	}


	@Nested
	class ContextTests {

		@Test
		void doNotRegisterBindingForContextArguments() {
			processController(ContextArgumentsController.class);
			assertThatHintsAreNotRegisteredForTypes(GraphQLContext.class, DataFetchingFieldSelectionSet.class, Locale.class);
		}

		@Test
		void doNotRegisterBindingForAnnotatedContextArguments() {
			processController(AnnotatedContextArgumentController.class);
			assertThatHintsAreNotRegisteredForTypes(Book.class);
		}

		@Controller
		static class ContextArgumentsController {
			@SchemaMapping
			public void dataFetchingEnvironment(GraphQLContext context, DataFetchingFieldSelectionSet selectionSet, Locale locale) {
			}
		}

		@Controller
		class AnnotatedContextArgumentController {
			@SchemaMapping
			public void contextValue(@ContextValue Book book, @LocalContextValue Book localBook) {
			}
		}

	}

	@Nested
	class ProjectionTests {

		@Test
		void registerSpringDataSpelSupport() {
			processController();
			TypeReference targetWrapper = TypeReference.of("org.springframework.data.projection.SpelEvaluatingMethodInterceptor$TargetWrapper");
			assertThat(RuntimeHintsPredicates.reflection().onType(targetWrapper)
					.withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS,
							MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(generationContext.getRuntimeHints());
		}

		@Test
		void registerProxyForProjection() {
			processController(ProjectionController.class);
			assertThat(RuntimeHintsPredicates.proxies().forInterfaces(BookProjection.class, TargetAware.class,
					SpringProxy.class, DecoratingProxy.class)).accepts(generationContext.getRuntimeHints());
		}


		@Test
		void registerProxyForOptionalProjection() {
			processController(OptionalProjectionController.class);
			assertThat(RuntimeHintsPredicates.proxies().forInterfaces(BookProjection.class, TargetAware.class,
					SpringProxy.class, DecoratingProxy.class)).accepts(generationContext.getRuntimeHints());
		}


		@Controller
		class ProjectionController {
			@QueryMapping
			public List<Book> projection(@Argument(name = "where") BookProjection projection) {
				return null;
			}
		}

		@Controller
		class OptionalProjectionController {
			@QueryMapping
			public List<Book> optionalProjection(@Argument(name = "where") Optional<BookProjection> projection) {
				return null;
			}
		}

		@ProjectedPayload
		interface BookProjection {
			String getAuthor();
		}

	}


	private void processController(Class<?>... controllers) {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		for (Class<?> beanClass : controllers) {
			beanFactory.registerBeanDefinition(beanClass.getName(), new RootBeanDefinition(beanClass));
		}
		BeanFactoryInitializationAotContribution contribution = this.processor.processAheadOfTime(beanFactory);
		assertThat(contribution).isNotNull();
		contribution.applyTo(this.generationContext, mock(BeanFactoryInitializationCode.class));
	}

	private void assertThatHintsForJavaBeanBindingRegisteredForTypes(Class<?>... types) {
		Predicate<RuntimeHints> predicate = Arrays.stream(types)
				.map(this::javaBeanBindingOnType)
				.reduce(Predicate::and)
				.orElseThrow(() -> new IllegalArgumentException("Could not generate predicate for types " + types));
		assertThat(predicate).accepts(this.generationContext.getRuntimeHints());
	}

	private void assertThatHintsAreNotRegisteredForTypes(Class<?>... types) {
		Predicate<RuntimeHints> predicate = Arrays.stream(types)
				.map(type -> (Predicate<RuntimeHints>) RuntimeHintsPredicates.reflection().onType(type))
				.reduce(Predicate::and)
				.orElseThrow(() -> new IllegalArgumentException("Could not generate predicate for types " + types));
		assertThat(predicate).rejects(this.generationContext.getRuntimeHints());
	}


	private Predicate<RuntimeHints> javaBeanBindingOnType(Class<?> type) {
		Predicate<RuntimeHints> predicate = RuntimeHintsPredicates.reflection().onType(type)
				.withMemberCategories(MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
		try {
			BeanInfo beanInfo = Introspector.getBeanInfo(type);
			PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
			for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
				Method readMethod = propertyDescriptor.getReadMethod();
				if (readMethod != null && readMethod.getDeclaringClass() != Object.class) {
					predicate = predicate.and(RuntimeHintsPredicates.reflection().onMethod(readMethod));
				}
				Method writeMethod = propertyDescriptor.getWriteMethod();
				if (writeMethod != null && writeMethod.getDeclaringClass() != Object.class) {
					predicate = predicate.and(RuntimeHintsPredicates.reflection().onMethod(writeMethod));
				}
			}
		}
		catch (IntrospectionException e) {
			// ignoring type
		}
		return predicate;
	}

}