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

package org.springframework.graphql.data;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Predicate;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.data.querydsl.binding.QuerydslPredicateBuilder;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.Streamable;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Entrypoint to create {@link DataFetcher} using repositories through Querydsl.
 * Exposes builders accepting {@link QuerydslPredicateExecutor} or
 * {@link ReactiveQuerydslPredicateExecutor} that support customization of bindings
 * and interface- and DTO projections. Instances can be created through a
 * {@link #builder(QuerydslPredicateExecutor) builder} to query for
 * {@link Builder#single()} or {@link Builder#many()} objects.
 * <p>Example:
 * <pre class="code">
 * interface BookRepository extends Repository&lt;Book, String&gt;,
 *                              QuerydslPredicateExecutor&lt;Book&gt;{}
 *
 * BookRepository repository = …;
 * TypeRuntimeWiring wiring = …;
 *
 * wiring.dataFetcher("books", QuerydslDataFetcher.builder(repository).many())
 *       .dataFetcher("book", QuerydslDataFetcher.builder(repository).single());
 * </pre>
 *
 * <p>
 * {@link DataFetcher} returning reactive types such as {@link Mono} and {@link Flux}
 * can be constructed from a {@link ReactiveQuerydslPredicateExecutor} using
 * {@link #builder(ReactiveQuerydslPredicateExecutor) builder}.
 * <p>For example:
 * <pre class="code">
 * interface BookRepository extends Repository&lt;Book, String&gt;,
 *                              ReactiveQuerydslPredicateExecutor&lt;Book&gt;{}
 *
 * BookRepository repository = …;
 * TypeRuntimeWiring wiring = …;
 *
 * wiring.dataFetcher("books", QuerydslDataFetcher.builder(repository).many())
 *       .dataFetcher("book", QuerydslDataFetcher.builder(repository).single());
 * </pre>
 *
 * @param <T> returned result type
 * @author Mark Paluch
 * @since 1.0.0
 * @see QuerydslPredicateExecutor
 * @see ReactiveQuerydslPredicateExecutor
 * @see Predicate
 * @see QuerydslBinderCustomizer
 */
public abstract class QuerydslDataFetcher<T> {

	private static final QuerydslPredicateBuilder BUILDER = new QuerydslPredicateBuilder(
			DefaultConversionService
					.getSharedInstance(), SimpleEntityPathResolver.INSTANCE);

	private final TypeInformation<T> domainType;

	private final QuerydslBinderCustomizer<EntityPath<?>> customizer;

	QuerydslDataFetcher(ClassTypeInformation<T> domainType,
			QuerydslBinderCustomizer<EntityPath<?>> customizer) {
		this.customizer = customizer;
		this.domainType = domainType;
	}

	/**
	 * Create a new {@link Builder} accepting {@link QuerydslPredicateExecutor}
	 * to build a {@link DataFetcher}.
	 * @param executor the repository object to use
	 * @param <T> result type
	 * @return a new builder
	 */
	@SuppressWarnings("unchecked")
	public static <T> Builder<T, T> builder(QuerydslPredicateExecutor<T> executor) {

		Class<?> repositoryInterface = getRepositoryInterface(executor);
		DefaultRepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);

		return new Builder<>(executor, (ClassTypeInformation<T>) ClassTypeInformation
				.from(metadata.getDomainType()), (bindings, root) -> {
		}, Function.identity());
	}

	/**
	 * Create a new {@link ReactiveBuilder} accepting
	 * {@link ReactiveQuerydslPredicateExecutor} to build a reactive {@link DataFetcher}.
	 * @param executor the repository object to use
	 * @param <T> result type
	 * @return a new builder
	 */
	@SuppressWarnings("unchecked")
	public static <T> ReactiveBuilder<T, T> builder(ReactiveQuerydslPredicateExecutor<T> executor) {

		Class<?> repositoryInterface = getRepositoryInterface(executor);
		DefaultRepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);

		return new ReactiveBuilder<>(executor, (ClassTypeInformation<T>) ClassTypeInformation
				.from(metadata.getDomainType()), (bindings, root) -> {
		}, Function.identity());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	Predicate buildPredicate(DataFetchingEnvironment environment) {
		MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		QuerydslBindings bindings = new QuerydslBindings();

		EntityPath<?> path = SimpleEntityPathResolver.INSTANCE
				.createPath(domainType.getType());

		customizer.customize(bindings, path);

		for (Map.Entry<String, Object> entry : environment.getArguments().entrySet()) {
			parameters.put(entry.getKey(), Collections.singletonList(entry.getValue()));
		}

		return BUILDER.getPredicate(domainType, (MultiValueMap) parameters, bindings);
	}

	private static <S, T> Function<S, T> createProjection(Class<T> projectionType) {
		// TODO: SpelAwareProxyProjectionFactory, DtoMappingContext, and EntityInstantiators
		//  should be reused to avoid duplicate class metadata.
		Assert.notNull(projectionType, "Projection type must not be null");

		if (projectionType.isInterface()) {
			ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
			return element -> projectionFactory
					.createProjection(projectionType, element);
		}

		DtoInstantiatingConverter<T> converter = new DtoInstantiatingConverter<>(projectionType,
				new DtoMappingContext(), new EntityInstantiators());
		return converter::convert;
	}

	private static Class<?> getRepositoryInterface(Object executor) {

		Assert.isInstanceOf(Repository.class, executor);

		Type[] genericInterfaces = executor.getClass().getGenericInterfaces();
		for (Type genericInterface : genericInterfaces) {

			ResolvableType resolvableType = ResolvableType.forType(genericInterface);

			if (resolvableType.getRawClass() == null || MergedAnnotations
					.from(resolvableType.getRawClass())
					.isPresent(NoRepositoryBean.class)) {
				continue;
			}

			if (Repository.class.isAssignableFrom(resolvableType.getRawClass())) {
				return resolvableType.getRawClass();
			}
		}

		throw new IllegalArgumentException(String
				.format("Cannot resolve repository interface from %s", executor));
	}

	/**
	 * Builder for a Querydsl-based {@link DataFetcher}. Note that builder
	 * instances are immutable and return a new instance of the builder
	 * when calling configuration methods.
	 * @param <T> domain type
	 * @param <R> result type
	 */
	public static class Builder<T, R> {

		private final QuerydslPredicateExecutor<T> executor;

		private final ClassTypeInformation<T> domainType;

		private final QuerydslBinderCustomizer<? extends EntityPath<T>> customizer;

		private final Function<T, R> resultConverter;

		Builder(QuerydslPredicateExecutor<T> executor, ClassTypeInformation<T> domainType,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer,
				Function<T, R> resultConverter) {
			this.executor = executor;
			this.domainType = domainType;
			this.customizer = customizer;
			this.resultConverter = resultConverter;
		}

		/**
		 * Project results returned from the {@link QuerydslPredicateExecutor}
		 * into the target {@code projectionType}. Projection types can be
		 * either interfaces declaring getters for properties to expose or
		 * regular classes outside the entity type hierarchy for
		 * DTO projection.
		 * @param projectionType projection type
		 * @return a new {@link Builder} instance with all previously
		 * configured options and {@code projectionType} applied
		 */
		public <P> Builder<T, P> projectAs(Class<P> projectionType) {
			Assert.notNull(projectionType, "Projection type must not be null");
			return new Builder<>(executor, domainType, customizer, createProjection(projectionType));
		}

		/**
		 * Apply a {@link QuerydslBinderCustomizer}.
		 * @param customizer the customizer to customize bindings for the
		 * actual query
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code QuerydslBinderCustomizer} applied
		 */
		public Builder<T, R> customizer(QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {
			Assert.notNull(customizer, "QuerydslBinderCustomizer must not be null");
			return new Builder<>(executor, domainType, customizer, resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 * @return a {@link DataFetcher} based on Querydsl to fetch one object
		 */
		public DataFetcher<R> single() {
			return new SingleEntityFetcher<>(executor, domainType, customizer, resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 * @return a {@link DataFetcher} based on Querydsl to fetch many objects
		 */
		public DataFetcher<Iterable<R>> many() {
			return new ManyEntityFetcher<>(executor, domainType, customizer, resultConverter);
		}

	}

	/**
	 * Builder for a reactive Querydsl-based {@link DataFetcher}. Note that builder
	 * instances are immutable and return a new instance of the builder when
	 * calling configuration methods.
	 * @param <T> domain type
	 * @param <R> result type
	 */
	public static class ReactiveBuilder<T, R> {

		private final ReactiveQuerydslPredicateExecutor<T> executor;

		private final ClassTypeInformation<T> domainType;

		private final QuerydslBinderCustomizer<? extends EntityPath<T>> customizer;

		private final Function<T, R> resultConverter;

		ReactiveBuilder(ReactiveQuerydslPredicateExecutor<T> executor,
				ClassTypeInformation<T> domainType,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer,
				Function<T, R> resultConverter) {
			this.executor = executor;
			this.domainType = domainType;
			this.customizer = customizer;
			this.resultConverter = resultConverter;
		}

		/**
		 * Project results returned from the {@link QuerydslPredicateExecutor}
		 * into the target {@code projectionType}. Projection types can be
		 * either interfaces declaring getters for properties to expose or
		 * regular classes outside the entity type hierarchy for
		 * DTO projection.
		 * @param projectionType projection type
		 * @return a new {@link Builder} instance with all previously
		 * configured options and {@code projectionType} applied
		 */
		public <P> ReactiveBuilder<T, P> projectAs(Class<P> projectionType) {
			Assert.notNull(projectionType, "Projection type must not be null");
			return new ReactiveBuilder<>(executor, domainType, customizer, createProjection(projectionType));
		}

		/**
		 * Apply a {@link QuerydslBinderCustomizer}.
		 * @param customizer the customizer to customize bindings for the
		 * actual query
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code QuerydslBinderCustomizer} applied
		 */
		public ReactiveBuilder<T, R> customizer(QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {
			Assert.notNull(customizer, "QuerydslBinderCustomizer must not be null");
			return new ReactiveBuilder<>(executor, domainType, customizer, resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances through {@link Mono}.
		 * @return a {@link DataFetcher} based on Querydsl to fetch one object
		 */
		public DataFetcher<Mono<R>> single() {
			return new ReactiveSingleEntityFetcher<>(executor, domainType, customizer, resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances through {@link Flux}.
		 * @return a {@link DataFetcher} based on Querydsl to fetch many objects
		 */
		public DataFetcher<Flux<R>> many() {
			return new ReactiveManyEntityFetcher<>(executor, domainType, customizer, resultConverter);
		}

	}

	static class SingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<R> {

		private final QuerydslPredicateExecutor<T> executor;

		private final Function<T, R> resultConverter;

		@SuppressWarnings({"unchecked", "rawtypes"})
		SingleEntityFetcher(QuerydslPredicateExecutor<T> executor,
				ClassTypeInformation<T> domainType,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer,
				Function<T, R> resultConverter) {
			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultConverter = resultConverter;
		}

		@Override
		public R get(DataFetchingEnvironment environment) {
			return executor.findOne(buildPredicate(environment)).map(resultConverter)
					.orElse(null);
		}

	}

	static class ManyEntityFetcher<T, R> extends QuerydslDataFetcher<T>
			implements DataFetcher<Iterable<R>> {

		private final QuerydslPredicateExecutor<T> executor;

		private final Function<T, R> resultConverter;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ManyEntityFetcher(QuerydslPredicateExecutor<T> executor,
				ClassTypeInformation<T> domainType,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer,
				Function<T, R> resultConverter) {
			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultConverter = resultConverter;
		}

		@Override
		public Iterable<R> get(DataFetchingEnvironment environment) {
			return Streamable.of(executor.findAll(buildPredicate(environment)))
					.map(resultConverter).toList();
		}

	}

	static class ReactiveSingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Mono<R>> {

		private final ReactiveQuerydslPredicateExecutor<T> executor;

		private final Function<T, R> resultConverter;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ReactiveSingleEntityFetcher(ReactiveQuerydslPredicateExecutor<T> executor,
				ClassTypeInformation<T> domainType,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer,
				Function<T, R> resultConverter) {
			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultConverter = resultConverter;
		}

		@Override
		public Mono<R> get(DataFetchingEnvironment environment) {
			return executor.findOne(buildPredicate(environment)).map(resultConverter);
		}

	}

	static class ReactiveManyEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Flux<R>> {

		private final ReactiveQuerydslPredicateExecutor<T> executor;

		private final Function<T, R> resultConverter;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ReactiveManyEntityFetcher(ReactiveQuerydslPredicateExecutor<T> executor,
				ClassTypeInformation<T> domainType,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer,
				Function<T, R> resultConverter) {
			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultConverter = resultConverter;
		}

		@Override
		public Flux<R> get(DataFetchingEnvironment environment) {
			return executor.findAll(buildPredicate(environment)).map(resultConverter);
		}

	}

}
