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

package org.springframework.graphql.data.query;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Predicate;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLTypeVisitor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.data.querydsl.binding.QuerydslPredicateBuilder;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;
import org.springframework.data.util.TypeInformation;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
 * <p>See {@link Builder} and {@link ReactiveBuilder} methods for further
 * options on GraphQL Query argument to Querydsl Predicate binding customizations,
 * result projections, and sorting.
 *
 * <p>{@code QuerydslDataFetcher} {@link #autoRegistrationConfigurer(List, List) exposes}
 * a {@link RuntimeWiringConfigurer} that can auto-register repositories
 * annotated with {@link GraphQlRepository @GraphQlRepository}.
 *
 * @param <T> returned result type
 * @author Mark Paluch
 * @author Rossen Stoyanchev
 * @since 1.0.0
 *
 * @see GraphQlRepository
 * @see QuerydslPredicateExecutor
 * @see ReactiveQuerydslPredicateExecutor
 * @see Predicate
 * @see QuerydslBinderCustomizer
 * @see <a href="https://docs.spring.io/spring-data/commons/docs/current/reference/html/#core.extensions.querydsl">
 * Spring Data Querydsl extension</a>
 */
public abstract class QuerydslDataFetcher<T> {

	private final static Log logger = LogFactory.getLog(QueryByExampleDataFetcher.class);

	private static final QuerydslPredicateBuilder BUILDER = new QuerydslPredicateBuilder(
			DefaultConversionService.getSharedInstance(), SimpleEntityPathResolver.INSTANCE);

	@SuppressWarnings("rawtypes")
	private static final QuerydslBinderCustomizer NO_OP_BINDER_CUSTOMIZER = (bindings, root) -> {};


	private final TypeInformation<T> domainType;

	private final QuerydslBinderCustomizer<EntityPath<?>> customizer;


	QuerydslDataFetcher(TypeInformation<T> domainType, QuerydslBinderCustomizer<EntityPath<?>> customizer) {
		this.domainType = domainType;
		this.customizer = customizer;
	}


	/**
	 * Prepare a {@link Predicate} from GraphQL request arguments, also applying
	 * any {@link QuerydslBinderCustomizer} that may have been configured.
	 * @param environment contextual info for the GraphQL request
	 * @return the resulting predicate
	 */
	@SuppressWarnings({"unchecked"})
	protected Predicate buildPredicate(DataFetchingEnvironment environment) {
		MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
		QuerydslBindings bindings = new QuerydslBindings();

		EntityPath<?> path = SimpleEntityPathResolver.INSTANCE.createPath(this.domainType.getType());
		this.customizer.customize(bindings, path);

		for (Map.Entry<String, Object> entry : environment.getArguments().entrySet()) {
			Object value = entry.getValue();
			List<Object> values = (value instanceof List ? (List<Object>) value : Collections.singletonList(value));
			parameters.put(entry.getKey(), values);
		}

		return BUILDER.getPredicate(this.domainType, parameters, bindings);
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
		return new Builder<>(executor, RepositoryUtils.getDomainType(executor));
	}

	/**
	 * Create a new {@link ReactiveBuilder} accepting
	 * {@link ReactiveQuerydslPredicateExecutor} to build a reactive {@link DataFetcher}.
	 * @param executor the repository object to use
	 * @param <T> result type
	 * @return a new builder
	 */
	public static <T> ReactiveBuilder<T, T> builder(ReactiveQuerydslPredicateExecutor<T> executor) {
		return new ReactiveBuilder<>(executor, RepositoryUtils.getDomainType(executor));
	}

	/**
	 * Return a {@link RuntimeWiringConfigurer} that installs a
	 * {@link graphql.schema.idl.WiringFactory} to find queries with a return
	 * type whose name matches to the domain type name of the given repositories
	 * and registers {@link DataFetcher}s for them.
	 *
	 * <p><strong>Note:</strong> This applies only to top-level queries and
	 * repositories annotated with {@link GraphQlRepository @GraphQlRepository}.
	 * If a repository is also an instance of {@link QuerydslBinderCustomizer},
	 * this is transparently detected and applied through the
	 * {@code QuerydslDataFetcher} builder  methods.
	 *
	 * @param executors repositories to consider for registration
	 * @param reactiveExecutors reactive repositories to consider for registration
	 * @return the created configurer
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static RuntimeWiringConfigurer autoRegistrationConfigurer(
			List<QuerydslPredicateExecutor<?>> executors,
			List<ReactiveQuerydslPredicateExecutor<?>> reactiveExecutors) {

		Map<String, Function<Boolean, DataFetcher<?>>> factories = new HashMap<>();

		for (QuerydslPredicateExecutor<?> executor : executors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				Builder builder = customize(executor, QuerydslDataFetcher.builder(executor).customizer(customizer(executor)));
				factories.put(typeName, single -> single ? builder.single() : builder.many());
			}
		}

		for (ReactiveQuerydslPredicateExecutor<?> executor : reactiveExecutors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				ReactiveBuilder builder = customize(executor, QuerydslDataFetcher.builder(executor).customizer(customizer(executor)));
				factories.put(typeName, single -> single ? builder.single() : builder.many());
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Auto-registration candidate typeNames " + factories.keySet());
		}

		return new AutoRegistrationRuntimeWiringConfigurer(factories);
	}

	/**
	 * Return a {@link GraphQLTypeVisitor} that auto-registers the given
	 * Querydsl repositories for queries that do not already have a registered
	 * {@code DataFetcher} and whose return type matches the simple name of the
	 * repository domain type.
	 *
	 * <p><strong>Note:</strong> Auto-registration applies only to
	 * {@link GraphQlRepository @GraphQlRepository}-annotated repositories.
	 * If a repository is also an instance of {@link QuerydslBinderCustomizer},
	 * this is transparently detected and applied through the
	 * {@code QuerydslDataFetcher} builder  methods.
	 *
	 * @param executors repositories to consider for registration
	 * @param reactiveExecutors reactive repositories to consider for registration
	 * @return the created visitor
	 * @deprecated in favor of {@link #autoRegistrationConfigurer(List, List)}
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Deprecated
	public static GraphQLTypeVisitor autoRegistrationTypeVisitor(
			List<QuerydslPredicateExecutor<?>> executors,
			List<ReactiveQuerydslPredicateExecutor<?>> reactiveExecutors) {

		Map<String, Function<Boolean, DataFetcher<?>>> factories = new HashMap<>();

		for (QuerydslPredicateExecutor<?> executor : executors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				Builder<?, ?> builder = customize(executor, QuerydslDataFetcher.builder(executor).customizer(customizer(executor)));
				factories.put(typeName, single -> single ? builder.single() : builder.many());
			}
		}

		for (ReactiveQuerydslPredicateExecutor<?> executor : reactiveExecutors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				ReactiveBuilder builder = customize(executor, QuerydslDataFetcher.builder(executor).customizer(customizer(executor)));
				factories.put(typeName, single -> single ? builder.single() : builder.many());
			}
		}

		return new AutoRegistrationTypeVisitor(factories);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Builder customize(QuerydslPredicateExecutor<?> executor, Builder builder) {
		if(executor instanceof QuerydslBuilderCustomizer<?> customizer){
			return customizer.customize(builder);
		}
		return builder;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ReactiveBuilder customize(ReactiveQuerydslPredicateExecutor<?> executor, ReactiveBuilder builder) {
		if(executor instanceof ReactiveQuerydslBuilderCustomizer<?> customizer){
			return customizer.customize(builder);
		}
		return builder;
	}

	@SuppressWarnings("rawtypes")
	private static QuerydslBinderCustomizer customizer(Object executor) {
		return (executor instanceof QuerydslBinderCustomizer<?> ?
				(QuerydslBinderCustomizer<? extends EntityPath<?>>) executor :
				NO_OP_BINDER_CUSTOMIZER);
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

		private final TypeInformation<T> domainType;

		private final Class<R> resultType;

		private final Sort sort;

		private final QuerydslBinderCustomizer<? extends EntityPath<T>> customizer;

		@SuppressWarnings("unchecked")
		Builder(QuerydslPredicateExecutor<T> executor, Class<R> domainType) {
			this(executor,
					TypeInformation.of((Class<T>) domainType),
					domainType,
					Sort.unsorted(),
					NO_OP_BINDER_CUSTOMIZER);
		}

		Builder(QuerydslPredicateExecutor<T> executor, TypeInformation<T> domainType,
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
		 * either interfaces with property getters to expose or regular classes
		 * outside the entity type hierarchy for DTO projections.
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
		 *
		 * <p>If a Querydsl repository implements {@link QuerydslBinderCustomizer}
		 * itself, this is automatically detected and applied during
		 * {@link #autoRegistrationConfigurer(List, List) auto-registration}.
		 * For manual registration, you will need to use this method to apply it.
		 *
		 * @param customizer to customize the binding of the GraphQL request to
		 * Querydsl Predicate
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code QuerydslBinderCustomizer} applied
		 */
		public Builder<T, R> customizer(QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {
			Assert.notNull(customizer, "QuerydslBinderCustomizer must not be null");
			return new Builder<>(this.executor, this.domainType, this.resultType, this.sort, customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 */
		public DataFetcher<R> single() {
			return new SingleEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 */
		public DataFetcher<Iterable<R>> many() {
			return new ManyEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
		}

	}


	/**
	 * Callback interface that can be used to customize QuerydslDataFetcher
	 * {@link Builder} to change its configuration. 
	 * <p>This is supported by {@link #autoRegistrationConfigurer(List, List)
	 * Auto-registration}, which detects if a repository implements this
	 * interface and applies it accordingly.
	 *
	 * @param <T>
	 * @since 1.1.1
	 */
	public interface QuerydslBuilderCustomizer<T> {

		/**
		 * Callback to customize a {@link Builder} instance.
		 * @param builder builder to customize
		 */
		Builder<T, ?> customize(Builder<T, ?> builder);

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
					TypeInformation.of((Class<T>) domainType),
					domainType,
					Sort.unsorted(),
					NO_OP_BINDER_CUSTOMIZER);
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
		 * Project results returned from the {@link ReactiveQuerydslPredicateExecutor}
		 * into the target {@code projectionType}. Projection types can be
		 * either interfaces with property getters to expose or regular classes
		 * outside the entity type hierarchy for DTO projections.
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
		 *
		 * <p>If a Querydsl repository implements {@link QuerydslBinderCustomizer}
		 * itself, this is automatically detected and applied during
		 * {@link #autoRegistrationConfigurer(List, List) auto-registration}.
		 * For manual registration, you will need to use this method to apply it.
		 *
		 * @param customizer to customize the GraphQL query to Querydsl
		 * Predicate binding with
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code QuerydslBinderCustomizer} applied
		 */
		public ReactiveBuilder<T, R> customizer(QuerydslBinderCustomizer<? extends EntityPath<T>> customizer) {
			Assert.notNull(customizer, "QuerydslBinderCustomizer must not be null");
			return new ReactiveBuilder<>(this.executor, this.domainType, this.resultType, this.sort, customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances}.
		 */
		public DataFetcher<Mono<R>> single() {
			return new ReactiveSingleEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 */
		public DataFetcher<Flux<R>> many() {
			return new ReactiveManyEntityFetcher<>(
					this.executor, this.domainType, this.resultType, this.sort, this.customizer);
		}

	}


	/**
	 * Callback interface that can be used to customize QuerydslDataFetcher
	 * {@link ReactiveBuilder} to change its configuration.
	 * <p>This is supported by {@link #autoRegistrationConfigurer(List, List)
	 * Auto-registration}, which detects if a repository implements this
	 * interface and applies it accordingly.
	 *
	 * @param <T>
	 * @since 1.1.1
	 */
	public interface ReactiveQuerydslBuilderCustomizer<T> {

		/**
		 * Callback to customize a {@link ReactiveBuilder} instance.
		 * @param builder builder to customize
		 */
		ReactiveBuilder<T, ?> customize(ReactiveBuilder<T, ?> builder);

	}


	private static class SingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements SelfDescribingDataFetcher<R> {

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

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClass(this.resultType);
		}
	}


	private static class ManyEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements SelfDescribingDataFetcher<Iterable<R>> {

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

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClassWithGenerics(Iterable.class, this.resultType);
		}

	}


	private static class ReactiveSingleEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements SelfDescribingDataFetcher<Mono<R>> {

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

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClassWithGenerics(Mono.class, this.resultType);
		}

	}


	private static class ReactiveManyEntityFetcher<T, R> extends QuerydslDataFetcher<T> implements SelfDescribingDataFetcher<Flux<R>> {

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

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClassWithGenerics(Flux.class, this.resultType);
		}

	}

}
