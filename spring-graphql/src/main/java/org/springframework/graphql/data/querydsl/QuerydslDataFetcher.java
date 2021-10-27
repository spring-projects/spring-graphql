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

package org.springframework.graphql.data.querydsl;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Predicate;
import graphql.schema.*;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Sort;
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
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Main class to create a {@link DataFetcher} from a Querydsl repository.
 * To create an instance, use one of the following:
 * <ul>
 * <li>{@link #builder(QuerydslPredicateExecutor)}
 * <li>{@link #builder(ReactiveQuerydslPredicateExecutor)}
 * </ul>
 *
 * <p>For example:
 *
 * <pre class="code">
 * interface BookRepository extends
 *         Repository&lt;Book, String&gt;, QuerydslPredicateExecutor&lt;Book&gt;{}
 *
 * TypeRuntimeWiring wiring = … ;
 * BookRepository repository = … ;
 *
 * DataFetcher&lt;?&gt; forMany =
 *         wiring.dataFetcher("books", QuerydslDataFetcher.builder(repository).many());
 *
 * DataFetcher&lt;?&gt; forSingle =
 *         wiring.dataFetcher("book", QuerydslDataFetcher.builder(repository).single());
 * </pre>
 *
 * <p>See methods on {@link Builder} and {@link ReactiveBuilder} for further
 * options on GraphQL Query argument to Querydsl Predicate bindings, result
 * projections, and sorting.
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


	QuerydslDataFetcher(TypeInformation<T> domainType, QuerydslBinderCustomizer<EntityPath<?>> customizer) {
		this.domainType = domainType;
		this.customizer = customizer;
	}


	/**
	 * Prepare a {@link Predicate} from GraphQL query arguments, also applying
	 * any {@link QuerydslBinderCustomizer} that may have been configured.
	 * @param environment contextual info for the GraphQL query
	 * @return the resulting predicate
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	protected Predicate buildPredicate(DataFetchingEnvironment environment) {
		MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		QuerydslBindings bindings = new QuerydslBindings();

		EntityPath<?> path = SimpleEntityPathResolver.INSTANCE.createPath(this.domainType.getType());
		this.customizer.customize(bindings, path);

		for (Map.Entry<String, Object> entry : environment.getArguments().entrySet()) {
			parameters.put(entry.getKey(), Collections.singletonList(entry.getValue()));
		}

		return BUILDER.getPredicate(this.domainType, (MultiValueMap) parameters, bindings);
	}

	protected boolean requiresProjection(Class<?> resultType) {
		return !resultType.equals(this.domainType.getType());
	}

	protected Collection<String> buildPropertyPaths(DataFetchingFieldSelectionSet selection, Class<?> resultType){

		// Compute selection only for non-projections
		if (this.domainType.getType().equals(resultType) ||
				this.domainType.getType().isAssignableFrom(resultType) ||
				this.domainType.isSubTypeOf(resultType)) {
			return PropertySelection.create(this.domainType, selection).toList();
		}
		return Collections.emptyList();
	}


	/**
	 * Create a new {@link Builder} accepting {@link QuerydslPredicateExecutor}
	 * to build a {@link DataFetcher}.
	 * @param executor the repository object to use
	 * @param <T> result type
	 * @return a new builder
	 */
	public static <T> Builder<T, T> builder(QuerydslPredicateExecutor<T> executor) {
		return new Builder<>(executor, getDomainType(executor));
	}

	/**
	 * Create a new {@link ReactiveBuilder} accepting
	 * {@link ReactiveQuerydslPredicateExecutor} to build a reactive {@link DataFetcher}.
	 * @param executor the repository object to use
	 * @param <T> result type
	 * @return a new builder
	 */
	public static <T> ReactiveBuilder<T, T> builder(ReactiveQuerydslPredicateExecutor<T> executor) {
		return new ReactiveBuilder<>(executor, getDomainType(executor));
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


	@SuppressWarnings("unchecked")
	private static <T> Class<T> getDomainType(Object executor) {
		Class<?> repositoryInterface = getRepositoryInterface(executor);
		DefaultRepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);
		return (Class<T>) metadata.getDomainType();
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

		private final Class<R> resultType;

		private final Sort sort;

		private final QuerydslBinderCustomizer<? extends EntityPath<T>> customizer;

		@SuppressWarnings("unchecked")
		Builder(QuerydslPredicateExecutor<T> executor, Class<R> domainType) {
			this(executor,
					ClassTypeInformation.from((Class<T>) domainType),
					domainType,
					Sort.unsorted(),
					(bindings, root) -> {});
		}

		Builder(QuerydslPredicateExecutor<T> executor, ClassTypeInformation<T> domainType,
				Class<R> resultType, Sort sort, QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {

			this.executor = executor;
			this.domainType = domainType;
			this.resultType = resultType;
			this.sort = sort;
			this.customizer = customizer;
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
			return new Builder<>(this.executor, this.domainType, projectionType, this.sort, this.customizer);
		}

		/**
		 * Apply a {@link Sort} order.
		 * @param sort the default sort order
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code Sort} applied
		 */
		public Builder<T, R> sortBy(Sort sort) {
			Assert.notNull(sort, "Sort must not be null");
			return new Builder<>(this.executor, this.domainType, this.resultType, sort, customizer);
		}

		/**
		 * Apply a {@link QuerydslBinderCustomizer}.
		 * @param customizer to customize the GraphQL query to Querydsl Predicate binding
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code QuerydslBinderCustomizer} applied
		 */
		public Builder<T, R> customizer(QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {
			Assert.notNull(customizer, "QuerydslBinderCustomizer must not be null");
			return new Builder<>(this.executor, this.domainType, this.resultType, this.sort, customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 * @return a {@link DataFetcher} based on Querydsl to fetch one object
		 */
		public DataFetcher<R> single() {
			return new SingleEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 * @return a {@link DataFetcher} based on Querydsl to fetch many objects
		 */
		public DataFetcher<Iterable<R>> many() {
			return new ManyEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
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

		private final TypeInformation<T> domainType;

		private final Class<R> resultType;

		private final Sort sort;

		private final QuerydslBinderCustomizer<? extends EntityPath<T>> customizer;

		@SuppressWarnings("unchecked")
		ReactiveBuilder(ReactiveQuerydslPredicateExecutor<T> executor, Class<R> domainType) {
			this(executor,
					ClassTypeInformation.from((Class<T>) domainType),
					domainType,
					Sort.unsorted(),
					(bindings, root) -> {});
		}

		ReactiveBuilder(ReactiveQuerydslPredicateExecutor<T> executor,
				TypeInformation<T> domainType,
				Class<R> resultType,
				Sort sort,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {

			this.executor = executor;
			this.domainType = domainType;
			this.resultType = resultType;
			this.sort = sort;
			this.customizer = customizer;
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
			return new ReactiveBuilder<>(this.executor, this.domainType, projectionType, this.sort, this.customizer);
		}

		/**
		 * Apply a {@link Sort} order.
		 * @param sort the default sort order
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code Sort} applied
		 */
		public ReactiveBuilder<T, R> sortBy(Sort sort) {
			Assert.notNull(sort, "Sort must not be null");
			return new ReactiveBuilder<>(this.executor, this.domainType, this.resultType, sort, customizer);
		}

		/**
		 * Apply a {@link QuerydslBinderCustomizer}.
		 * @param customizer to customize the GraphQL query to Querydsl Predicate binding
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code QuerydslBinderCustomizer} applied
		 */
		public ReactiveBuilder<T, R> customizer(QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {
			Assert.notNull(customizer, "QuerydslBinderCustomizer must not be null");
			return new ReactiveBuilder<>(this.executor, this.domainType, this.resultType, this.sort, customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances through {@link Mono}.
		 * @return a {@link DataFetcher} based on Querydsl to fetch one object
		 */
		public DataFetcher<Mono<R>> single() {
			return new ReactiveSingleEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances through {@link Flux}.
		 * @return a {@link DataFetcher} based on Querydsl to fetch many objects
		 */
		public DataFetcher<Flux<R>> many() {
			return new ReactiveManyEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
		}

	}


	private static class SingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<R> {

		private final QuerydslPredicateExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		SingleEntityFetcher(QuerydslPredicateExecutor<T> executor,
				TypeInformation<T> domainType,
				Class<R> resultType,
				Sort sort,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {

			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings({"ConstantConditions", "unchecked"})
		public R get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildPredicate(env), query -> {
				FetchableFluentQuery<R> queryToUse = (FetchableFluentQuery<R>) query;

				if (this.sort.isSorted()){
					queryToUse = queryToUse.sortBy(this.sort);
				}

				Class<R> resultType = this.resultType;
				if (requiresProjection(resultType)){
					queryToUse = queryToUse.as(resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), resultType));
				}

				return queryToUse.first();
			}).orElse(null);
		}

	}


	private static class ManyEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Iterable<R>> {

		private final QuerydslPredicateExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ManyEntityFetcher(QuerydslPredicateExecutor<T> executor,
				TypeInformation<T> domainType,
				Class<R> resultType,
				Sort sort,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {
			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Iterable<R> get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildPredicate(env), query -> {
				FetchableFluentQuery<R> queryToUse = (FetchableFluentQuery<R>) query;

				if (this.sort.isSorted()){
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)){
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.all();
			});
		}

	}


	private static class ReactiveSingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Mono<R>> {

		private final ReactiveQuerydslPredicateExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ReactiveSingleEntityFetcher(ReactiveQuerydslPredicateExecutor<T> executor,
				TypeInformation<T> domainType,
				Class<R> resultType,
				Sort sort,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {

			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Mono<R> get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildPredicate(env), query -> {
				FluentQuery.ReactiveFluentQuery<R> queryToUse = (FluentQuery.ReactiveFluentQuery<R>) query;

				if (this.sort.isSorted()){
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)){
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.first();
			});
		}

	}


	private static class ReactiveManyEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements DataFetcher<Flux<R>> {

		private final ReactiveQuerydslPredicateExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ReactiveManyEntityFetcher(ReactiveQuerydslPredicateExecutor<T> executor,
				TypeInformation<T> domainType,
				Class<R> resultType,
				Sort sort,
				QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {

			super(domainType, (QuerydslBinderCustomizer) customizer);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Flux<R> get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildPredicate(env), query -> {
				FluentQuery.ReactiveFluentQuery<R> queryToUse = (FluentQuery.ReactiveFluentQuery<R>) query;

				if (this.sort.isSorted()){
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)){
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.all();
			});
		}

	}


	/**
	 * GraphQLTypeVisitor that auto-registers Querydsl Spring Data repositories.
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

			Map<String, Function<Boolean, DataFetcher<?>>> map = new HashMap<>();

			for (QuerydslPredicateExecutor<?> executor : executors) {
				String typeName = getTypeName(executor);
				if (typeName != null) {
					map.put(typeName, (single) -> single ?
							QuerydslDataFetcher.builder(executor).single() :
							QuerydslDataFetcher.builder(executor).many());
				}
			}

			for (ReactiveQuerydslPredicateExecutor<?> reactiveExecutor : reactiveExecutors) {
				String typeName = getTypeName(reactiveExecutor);
				if (typeName != null) {
					map.put(typeName, (single) -> single ?
							QuerydslDataFetcher.builder(reactiveExecutor).single() :
							QuerydslDataFetcher.builder(reactiveExecutor).many());
				}
			}

			return map;
		}

		@Nullable
		private String getTypeName(Object repository) {
			GraphQlRepository annotation =
					AnnotatedElementUtils.findMergedAnnotation(repository.getClass(), GraphQlRepository.class);

			if (annotation == null) {
				return null;
			}
			if (StringUtils.hasText(annotation.typeName())) {
				return annotation.typeName();
			}
			Class<?> repositoryInterface = getRepositoryInterface(repository);
			RepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);
			return metadata.getDomainType().getSimpleName();
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
