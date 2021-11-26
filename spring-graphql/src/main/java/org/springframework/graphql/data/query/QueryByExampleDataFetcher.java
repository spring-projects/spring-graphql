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

package org.springframework.graphql.data.query;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.graphql.data.GraphQlArgumentInitializer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Main class to create a {@link DataFetcher} from a Query By Example repository.
 * To create an instance, use one of the following:
 * <ul>
 * <li>{@link #builder(QueryByExampleExecutor)}
 * <li>{@link #builder(ReactiveQueryByExampleExecutor)}
 * </ul>
 *
 * <p>For example:
 *
 * <pre class="code">
 * interface BookRepository extends
 *         Repository&lt;Book, String&gt;, QueryByExampleExecutor&lt;Book&gt;{}
 *
 * TypeRuntimeWiring wiring = … ;
 * BookRepository repository = … ;
 *
 * DataFetcher&lt;?&gt; forMany =
 *         wiring.dataFetcher("books", QueryByExampleDataFetcher.builder(repository).many());
 *
 * DataFetcher&lt;?&gt; forSingle =
 *         wiring.dataFetcher("book", QueryByExampleDataFetcher.builder(repository).single());
 * </pre>
 *
 * <p>See methods on {@link QueryByExampleDataFetcher.Builder} and
 * {@link QueryByExampleDataFetcher.ReactiveBuilder} for further options on
 * result projections and sorting.
 *
 * <p>{@code QueryByExampleDataFetcher} {@link #registrationTypeVisitor(List, List) exposes}
 * a {@link GraphQLTypeVisitor} that can auto-register repositories annotated with
 * {@link GraphQlRepository @GraphQlRepository}.
 *
 * @param <T> returned result type
 * @author Greg Turnquist
 * @author Rossen Stoyanchev
 * @since 1.0.0
 *
 * @see GraphQlRepository
 * @see QueryByExampleExecutor
 * @see ReactiveQueryByExampleExecutor
 * @see Example
 * @see <a href="https://docs.spring.io/spring-data/commons/docs/current/reference/html/#query-by-example">
 * Spring Data Query By Example extension</a>
 */
public abstract class QueryByExampleDataFetcher<T> {

	private final TypeInformation<T> domainType;

	private final GraphQlArgumentInitializer argumentInitializer;


	QueryByExampleDataFetcher(TypeInformation<T> domainType) {
		this.domainType = domainType;
		this.argumentInitializer = new GraphQlArgumentInitializer(null);
	}


	/**
	 * Prepare an {@link Example} from GraphQL query arguments.
	 * @param env contextual info for the GraphQL query
	 * @return the resulting example
	 */
	protected Example<T> buildExample(DataFetchingEnvironment env) {
		return Example.of(this.argumentInitializer.initializeFromMap(env.getArguments(), this.domainType.getType()));
	}

	protected boolean requiresProjection(Class<?> resultType) {
		return !resultType.equals(this.domainType.getType());
	}

	protected Collection<String> buildPropertyPaths(DataFetchingFieldSelectionSet selection, Class<?> resultType) {

		// Compute selection only for non-projections
		if (this.domainType.getType().equals(resultType) ||
				this.domainType.getType().isAssignableFrom(resultType) ||
				this.domainType.isSubTypeOf(resultType)) {
			return PropertySelection.create(this.domainType, selection).toList();
		}
		return Collections.emptyList();
	}


	/**
	 * Create a new {@link Builder} accepting {@link QueryByExampleExecutor}
	 * to build a {@link DataFetcher}.
	 *
	 * @param executor the QBE repository object to use
	 * @param <T> the domain type of the repository
	 * @return a new builder
	 */
	public static <T> Builder<T, T> builder(QueryByExampleExecutor<T> executor) {
		return new Builder<>(executor, getDomainType(executor));
	}

	/**
	 * Create a new {@link ReactiveBuilder} accepting
	 * {@link ReactiveQueryByExampleExecutor} to build a {@link DataFetcher}.
	 *
	 * @param executor the QBE repository object to use
	 * @param <T> the domain type of the repository
	 * @return a new builder
	 */
	public static <T> ReactiveBuilder<T, T> builder(ReactiveQueryByExampleExecutor<T> executor) {
		return new ReactiveBuilder<>(executor, getDomainType(executor));
	}

	/**
	 * Create a {@link GraphQLTypeVisitor} that finds queries with a return type
	 * whose name matches to the domain type name of the given repositories and
	 * registers {@link DataFetcher}s for those queries.
	 * <p><strong>Note:</strong> currently, this method will match only to
	 * queries under the top-level "Query" type in the GraphQL schema.
	 *
	 * @param executors         repositories to consider for registration
	 * @param reactiveExecutors reactive repositories to consider for registration
	 * @return the created visitor
	 */
	public static GraphQLTypeVisitor registrationTypeVisitor(
			List<QueryByExampleExecutor<?>> executors,
			List<ReactiveQueryByExampleExecutor<?>> reactiveExecutors) {

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
	 * Builder for a Query by Example-based {@link DataFetcher}. Note that builder
	 * instances are immutable and return a new instance of the builder
	 * when calling configuration methods.
	 *
	 * @param <T> domain type
	 * @param <R> result type
	 */
	public static class Builder<T, R> {

		private final QueryByExampleExecutor<T> executor;

		private final ClassTypeInformation<T> domainType;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings("unchecked")
		Builder(QueryByExampleExecutor<T> executor, Class<R> domainType) {
			this(executor, ClassTypeInformation.from((Class<T>) domainType), domainType, Sort.unsorted());
		}

		Builder(QueryByExampleExecutor<T> executor, ClassTypeInformation<T> domainType, Class<R> resultType, Sort sort) {
			this.executor = executor;
			this.domainType = domainType;
			this.resultType = resultType;
			this.sort = sort;
		}

		/**
		 * Project results returned from the {@link QueryByExampleExecutor}
		 * into the target {@code projectionType}. Projection types can be
		 * either interfaces with property getters to expose or regular classes
		 * outside the entity type hierarchy for DTO projections.
		 * @param projectionType projection type
		 * @return a new {@link Builder} instance with all previously
		 * configured options and {@code projectionType} applied
		 */
		public <P> Builder<T, P> projectAs(Class<P> projectionType) {
			Assert.notNull(projectionType, "Projection type must not be null");
			return new Builder<>(this.executor, this.domainType, projectionType, this.sort);
		}

		/**
		 * Apply a {@link Sort} order.
		 * @param sort the default sort order
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code Sort} applied
		 */
		public Builder<T, R> sortBy(Sort sort) {
			Assert.notNull(sort, "Sort must not be null");
			return new Builder<>(this.executor, this.domainType, this.resultType, sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 */
		public DataFetcher<R> single() {
			return new SingleEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 */
		public DataFetcher<Iterable<R>> many() {
			return new ManyEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

	}


	/**
	 * Builder for a reactive Query by Example-based {@link DataFetcher}.
	 * Note that builder instances are immutable and return a new instance of
	 * the builder when calling configuration methods.
	 *
	 * @param <T> domain type
	 * @param <R> result type
	 */
	public static class ReactiveBuilder<T, R> {

		private final ReactiveQueryByExampleExecutor<T> executor;

		private final TypeInformation<T> domainType;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings("unchecked")
		ReactiveBuilder(ReactiveQueryByExampleExecutor<T> executor, Class<R> domainType) {
			this(executor, ClassTypeInformation.from((Class<T>) domainType), domainType, Sort.unsorted());
		}

		ReactiveBuilder(
				ReactiveQueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			this.executor = executor;
			this.domainType = domainType;
			this.resultType = resultType;
			this.sort = sort;
		}

		/**
		 * Project results returned from the {@link ReactiveQueryByExampleExecutor}
		 * into the target {@code projectionType}. Projection types can be
		 * either interfaces with property getters to expose or regular classes
		 * outside the entity type hierarchy for DTO projections.
		 * @param projectionType projection type
		 * @return a new {@link ReactiveBuilder} instance with all previously
		 * configured options and {@code projectionType} applied
		 */
		public <P> ReactiveBuilder<T, P> projectAs(Class<P> projectionType) {
			Assert.notNull(projectionType, "Projection type must not be null");
			return new ReactiveBuilder<>(this.executor, this.domainType, projectionType, this.sort);
		}

		/**
		 * Apply a {@link Sort} order.
		 * @param sort the default sort order
		 * @return a new {@link ReactiveBuilder} instance with all previously configured
		 * options and {@code Sort} applied
		 */
		public ReactiveBuilder<T, R> sortBy(Sort sort) {
			Assert.notNull(sort, "Sort must not be null");
			return new ReactiveBuilder<>(this.executor, this.domainType, this.resultType, sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 */
		public DataFetcher<Mono<R>> single() {
			return new ReactiveSingleEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 */
		public DataFetcher<Flux<R>> many() {
			return new ReactiveManyEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

	}


	private static class SingleEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements DataFetcher<R> {

		private final QueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		SingleEntityFetcher(
				QueryByExampleExecutor<T> executor, TypeInformation<T> domainType, Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings({"ConstantConditions", "unchecked"})
		public R get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.FetchableFluentQuery<R> queryToUse = (FluentQuery.FetchableFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				Class<R> resultType = this.resultType;
				if (requiresProjection(resultType)) {
					queryToUse = queryToUse.as(resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), resultType));
				}

				return queryToUse.first();
			}).orElse(null);
		}

	}


	private static class ManyEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements DataFetcher<Iterable<R>> {

		private final QueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ManyEntityFetcher(
				QueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Iterable<R> get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.FetchableFluentQuery<R> queryToUse = (FluentQuery.FetchableFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)) {
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.all();
			});
		}

	}


	private static class ReactiveSingleEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements DataFetcher<Mono<R>> {

		private final ReactiveQueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ReactiveSingleEntityFetcher(
				ReactiveQueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Mono<R> get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.ReactiveFluentQuery<R> queryToUse = (FluentQuery.ReactiveFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)) {
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.first();
			});
		}

	}


	private static class ReactiveManyEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements DataFetcher<Flux<R>> {

		private final ReactiveQueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings({"unchecked", "rawtypes"})
		ReactiveManyEntityFetcher(
				ReactiveQueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Flux<R> get(DataFetchingEnvironment env) {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.ReactiveFluentQuery<R> queryToUse = (FluentQuery.ReactiveFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)) {
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
	 * GraphQLTypeVisitor that auto-registers Query By Example Spring Data repositories.
	 */
	private static class RegistrationTypeVisitor extends GraphQLTypeVisitorStub {

		private final Map<String, Function<Boolean, DataFetcher<?>>> executorMap;

		RegistrationTypeVisitor(
				List<QueryByExampleExecutor<?>> executors,
				List<ReactiveQueryByExampleExecutor<?>> reactiveExecutors) {

			this.executorMap = initExecutorMap(executors, reactiveExecutors);
		}

		private Map<String, Function<Boolean, DataFetcher<?>>> initExecutorMap(
				List<QueryByExampleExecutor<?>> executors,
				List<ReactiveQueryByExampleExecutor<?>> reactiveExecutors) {

			Map<String, Function<Boolean, DataFetcher<?>>> map = new HashMap<>();

			for (QueryByExampleExecutor<?> executor : executors) {
				String typeName = getTypeName(executor);
				if (typeName != null) {
					map.put(typeName, (single) -> single ?
							builder(executor).single() :
							builder(executor).many());
				}
			}

			for (ReactiveQueryByExampleExecutor<?> reactiveExecutor : reactiveExecutors) {
				String typeName = getTypeName(reactiveExecutor);
				if (typeName != null) {
					map.put(typeName, (single) -> single ?
							builder(reactiveExecutor).single() :
							builder(reactiveExecutor).many());
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
