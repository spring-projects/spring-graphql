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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Predicate;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.PropertyDataFetcher;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
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
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.Streamable;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Entry point to create {@link DataFetcher} using repositories through Querydsl.
 * Exposes builders accepting {@link QuerydslPredicateExecutor} or
 * {@link ReactiveQuerydslPredicateExecutor} that support customization of bindings
 * and interface- and DTO projections. Instances can be created through a
 * {@link #builder(QuerydslPredicateExecutor) builder} to query for
 * {@link Builder#single()} or {@link Builder#many()} objects.
 * <p>Example:
 * <pre class="code">
 * interface BookRepository extends
 *         Repository&lt;Book, String&gt;, QuerydslPredicateExecutor&lt;Book&gt;{}
 *
 * TypeRuntimeWiring wiring = … ;
 * BookRepository repository = … ;
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
 * interface BookRepository extends
 *         Repository&lt;Book, String&gt;, ReactiveQuerydslPredicateExecutor&lt;Book&gt;{}
 *
 * TypeRuntimeWiring wiring = …;
 * BookRepository repository = …;
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
 * @see <a href="https://docs.spring.io/spring-data/commons/docs/current/reference/html/#core.extensions.querydsl">
 * Spring Data Querydsl extension</a>
 */
public abstract class QuerydslDataFetcher<T> {

	private static final QuerydslPredicateBuilder BUILDER = new QuerydslPredicateBuilder(
			DefaultConversionService.getSharedInstance(), SimpleEntityPathResolver.INSTANCE);

	private final TypeInformation<T> domainType;

	private final QuerydslBinderCustomizer<EntityPath<?>> customizer;

	QuerydslDataFetcher(ClassTypeInformation<T> domainType, QuerydslBinderCustomizer<EntityPath<?>> customizer) {
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

		return new Builder<>(executor,
				(ClassTypeInformation<T>) ClassTypeInformation.from(metadata.getDomainType()),
				(bindings, root) -> {}, Function.identity());
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

		return new ReactiveBuilder<>(executor,
				(ClassTypeInformation<T>) ClassTypeInformation.from(metadata.getDomainType()),
				(bindings, root) -> {}, Function.identity());
	}

	/**
	 * Create a {@link GraphQLTypeVisitor} that finds queries with a return type
	 * whose name matches to the domain type name of the given repositories and
	 * registers {@link DataFetcher}s for those queries.
	 * <p><strong>Note:</strong> currently, this method will match only to
	 * queries under the top-level "Query" type in the GraphQL schema.
	 * @param executors repositories to consider for registration
	 * @param reactiveExecutors reactive repositories to consider for registration
	 * @return the created visitor
	 */
	public static GraphQLTypeVisitor registrationTypeVisitor(
			List<QuerydslPredicateExecutor<?>> executors,
			List<ReactiveQuerydslPredicateExecutor<?>> reactiveExecutors) {

		return new RegistrationTypeVisitor(executors, reactiveExecutors);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	protected Predicate buildPredicate(DataFetchingEnvironment environment) {
		MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		QuerydslBindings bindings = new QuerydslBindings();

		EntityPath<?> path = SimpleEntityPathResolver.INSTANCE.createPath(this.domainType.getType());
		this.customizer.customize(bindings, path);

		for (Map.Entry<String, Object> entry : environment.getArguments().entrySet()) {
			parameters.put(entry.getKey(), Collections.singletonList(entry.getValue()));
		}

		Predicate predicate = BUILDER.getPredicate(this.domainType, (MultiValueMap) parameters, bindings);

		// Temporary workaround for this fix in Spring Data:
		// https://github.com/spring-projects/spring-data-commons/issues/2396

		if (predicate == null) {
			predicate = new BooleanBuilder();
		}

		return predicate;
	}

	private static <S, T> Function<S, T> createProjection(Class<T> projectionType) {
		// TODO: SpelAwareProxyProjectionFactory, DtoMappingContext, and EntityInstantiators
		//  should be reused to avoid duplicate class metadata.
		Assert.notNull(projectionType, "Projection type must not be null");

		if (projectionType.isInterface()) {
			ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
			return element -> projectionFactory.createProjection(projectionType, element);
		}

		DtoInstantiatingConverter<T> converter = new DtoInstantiatingConverter<>(projectionType,
				new DtoMappingContext(), new EntityInstantiators());

		return converter::convert;
	}

	private static Class<?> getRepositoryInterface(Object executor) {
		Assert.isInstanceOf(Repository.class, executor);

		Type[] genericInterfaces = executor.getClass().getGenericInterfaces();
		for (Type genericInterface : genericInterfaces) {
			Class<?> rawClass = ResolvableType.forType(genericInterface).getRawClass();
			if (rawClass == null || MergedAnnotations.from(rawClass).isPresent(NoRepositoryBean.class)) {
				continue;
			}
			if (Repository.class.isAssignableFrom(rawClass)) {
				return rawClass;
			}
		}

		throw new IllegalArgumentException(
				String.format("Cannot resolve repository interface from %s", executor));
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
			return new Builder<>(
					this.executor, this.domainType, this.customizer, createProjection(projectionType));
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
			return new Builder<>(
					this.executor, this.domainType, customizer, this.resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 * @return a {@link DataFetcher} based on Querydsl to fetch one object
		 */
		public DataFetcher<R> single() {
			return new SingleEntityFetcher<>(
					this.executor, this.domainType, this.customizer, this.resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 * @return a {@link DataFetcher} based on Querydsl to fetch many objects
		 */
		public DataFetcher<Iterable<R>> many() {
			return new ManyEntityFetcher<>(
					this.executor, this.domainType, this.customizer, this.resultConverter);
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
			return new ReactiveBuilder<>(
					this.executor, this.domainType, this.customizer, createProjection(projectionType));
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
			return new ReactiveBuilder<>(
					this.executor, this.domainType, customizer, this.resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances through {@link Mono}.
		 * @return a {@link DataFetcher} based on Querydsl to fetch one object
		 */
		public DataFetcher<Mono<R>> single() {
			return new ReactiveSingleEntityFetcher<>(
					this.executor, this.domainType, this.customizer, this.resultConverter);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances through {@link Flux}.
		 * @return a {@link DataFetcher} based on Querydsl to fetch many objects
		 */
		public DataFetcher<Flux<R>> many() {
			return new ReactiveManyEntityFetcher<>(
					this.executor, this.domainType, this.customizer, this.resultConverter);
		}

	}

	private static class SingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<R> {

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
		@SuppressWarnings("ConstantConditions")
		public R get(DataFetchingEnvironment environment) {
			Predicate predicate = buildPredicate(environment);
			return this.executor.findOne(predicate).map(this.resultConverter).orElse(null);
		}

	}

	private static class ManyEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Iterable<R>> {

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
			Predicate predicate = buildPredicate(environment);
			return Streamable.of(this.executor.findAll(predicate)).map(this.resultConverter).toList();
		}

	}

	private static class ReactiveSingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Mono<R>> {

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
			return this.executor.findOne(buildPredicate(environment)).map(this.resultConverter);
		}

	}

	private static class ReactiveManyEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Flux<R>> {

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
			return this.executor.findAll(buildPredicate(environment)).map(this.resultConverter);
		}

	}


	/**
	 * Visitor that auto-registers Querydsl repositories.
	 */
	private static class RegistrationTypeVisitor extends GraphQLTypeVisitorStub {

		private final Map<String, Function<Boolean, DataFetcher<?>>> executorMap;

		RegistrationTypeVisitor(
				List<QuerydslPredicateExecutor<?>> executors,
				List<ReactiveQuerydslPredicateExecutor<?>> reactiveExecutors) {

			this.executorMap = initExecutorMap(executors, reactiveExecutors);
		}

		private Map<String, Function<Boolean, DataFetcher<?>>> initExecutorMap(
				List<QuerydslPredicateExecutor<?>> executors,
				List<ReactiveQuerydslPredicateExecutor<?>> reactiveExecutors) {

			int size = executors.size() + reactiveExecutors.size();
			Map<String, Function<Boolean, DataFetcher<?>>> map = new HashMap<>(size);

			for (QuerydslPredicateExecutor<?> executor : executors) {
				Class<?> repositoryInterface = getRepositoryInterface(executor);
				RepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);
				map.put(metadata.getDomainType().getSimpleName(), (single) -> single ?
						QuerydslDataFetcher.builder(executor).single() :
						QuerydslDataFetcher.builder(executor).many());
			}

			for (ReactiveQuerydslPredicateExecutor<?> reactiveExecutor : reactiveExecutors) {
				Class<?> repositoryInterface = getRepositoryInterface(reactiveExecutor);
				RepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);
				map.put(metadata.getDomainType().getSimpleName(), (single) -> single ?
						QuerydslDataFetcher.builder(reactiveExecutor).single() :
						QuerydslDataFetcher.builder(reactiveExecutor).many());
			}

			return map;
		}

		@Override
		public TraversalControl visitGraphQLFieldDefinition(
				GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

			if (this.executorMap.isEmpty()) {
				return TraversalControl.QUIT;
			}

			GraphQLType fieldType = fieldDefinition.getType();
			GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
			if (!parent.getName().equals("Query")) {
				return TraversalControl.ABORT;
			}

			DataFetcher<?> dataFetcher = (fieldType instanceof GraphQLList ?
					getDataFetcher(((GraphQLList) fieldType).getWrappedType(), false) :
					getDataFetcher(fieldType, true));

			if (dataFetcher != null) {
				GraphQLCodeRegistry.Builder registry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
				if (!hasDataFetcher(registry, parent, fieldDefinition)) {
					registry.dataFetcher(parent, fieldDefinition, dataFetcher);
				}
			}

			return TraversalControl.CONTINUE;
		}

		@Nullable
		private DataFetcher<?> getDataFetcher(GraphQLType type, boolean single) {
			if (type instanceof GraphQLNamedOutputType) {
				String typeName = ((GraphQLNamedOutputType) type).getName();
				Function<Boolean, DataFetcher<?>> factory = this.executorMap.get(typeName);
				if (factory != null) {
					return factory.apply(single);
				}
			}
			return null;
		}

		private boolean hasDataFetcher(
				GraphQLCodeRegistry.Builder registry, GraphQLFieldsContainer parent,
				GraphQLFieldDefinition fieldDefinition) {

			DataFetcher<?> fetcher = registry.getDataFetcher(parent, fieldDefinition);
			return (fetcher != null && !(fetcher instanceof PropertyDataFetcher));
		}
	}

}
