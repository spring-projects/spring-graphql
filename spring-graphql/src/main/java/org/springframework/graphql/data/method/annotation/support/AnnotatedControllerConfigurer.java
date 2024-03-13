/*
 * Copyright 2002-2024 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.RuntimeWiring;
import io.micrometer.context.ContextSnapshotFactory;
import org.dataloader.DataLoader;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.query.SortStrategy;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder;


/**
 * {@link RuntimeWiringConfigurer} that finds {@link SchemaMapping @SchemaMapping}
 * and {@link BatchMapping @BatchMapping} methods in {@link Controller @Controller}
 * classes, and registers them as {@link DataFetcher}s.
 *
 * <p>This class detects the following strategies in Spring configuration,
 * expecting to find a single, unique bean of that type:
 * <ul>
 * <li>{@link CursorStrategy} -- if Spring Data is present, and the strategy
 * supports {@code ScrollPosition}, then {@link ScrollSubrangeMethodArgumentResolver}
 * is configured for use. If not, then {@link SubrangeMethodArgumentResolver}
 * is added instead.
 * <li>{@link SortStrategy} -- if present, then {@link SortMethodArgumentResolver}
 * is configured for use.
 * </ul>
 *
 * <p>In addition to initializing a {@link RuntimeWiring.Builder}, this class, also
 * provides an option to {@link #configure(GraphQLCodeRegistry.Builder) configure}
 * data fetchers on a {@link GraphQLCodeRegistry.Builder}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class AnnotatedControllerConfigurer
		extends AnnotatedControllerDetectionSupport<DataFetcherMappingInfo>
		implements RuntimeWiringConfigurer {

	private static final ClassLoader classLoader = AnnotatedControllerConfigurer.class.getClassLoader();

	private final static boolean springDataPresent = ClassUtils.isPresent(
			"org.springframework.data.projection.SpelAwareProxyProjectionFactory", classLoader);

	private final static boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext", classLoader);

	private final static boolean beanValidationPresent = ClassUtils.isPresent(
			"jakarta.validation.executable.ExecutableValidator", classLoader);


	private final List<HandlerMethodArgumentResolver> customArgumentResolvers = new ArrayList<>(8);

	@Nullable
	private ValidationHelper validationHelper;


	/**
	 * Add a {@link HandlerMethodArgumentResolver} for custom controller method
	 * arguments. Such custom resolvers are ordered after built-in resolvers
	 * except for {@link SourceMethodArgumentResolver}, which is always last.
	 *
	 * @param resolver the resolver to add.
	 * @since 1.2.0
	 */
	public void addCustomArgumentResolver(HandlerMethodArgumentResolver resolver) {
		this.customArgumentResolvers.add(resolver);
	}

	/**
	 * Configure an initializer that configures the {@link DataBinder} before the binding process.
	 * @param consumer the data binder initializer
	 * @since 1.0.1
	 * @deprecated this property is deprecated, ignored, and should not be
	 * necessary as a {@link DataBinder} is no longer used to bind arguments
	 */
	@Deprecated(since = "1.1.0", forRemoval = true)
	public void setDataBinderInitializer(@Nullable Consumer<DataBinder> consumer) {
	}


	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();

		if (beanValidationPresent) {
			this.validationHelper = ValidationHelper.createIfValidatorPresent(obtainApplicationContext());
		}
	}

	@Override
	protected HandlerMethodArgumentResolverComposite initArgumentResolvers() {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();

		// Annotation based
		if (springDataPresent) {
			// Must be ahead of ArgumentMethodArgumentResolver
			resolvers.addResolver(new ProjectedPayloadMethodArgumentResolver(obtainApplicationContext()));
		}

		GraphQlArgumentBinder argumentBinder =
				new GraphQlArgumentBinder(getConversionService(), isFallBackOnDirectFieldAccess());

		resolvers.addResolver(new ArgumentMethodArgumentResolver(argumentBinder));
		resolvers.addResolver(new ArgumentsMethodArgumentResolver(argumentBinder));
		resolvers.addResolver(new ContextValueMethodArgumentResolver());
		resolvers.addResolver(new LocalContextValueMethodArgumentResolver());

		// Type based
		resolvers.addResolver(new DataFetchingEnvironmentMethodArgumentResolver());
		resolvers.addResolver(new DataLoaderMethodArgumentResolver());
		addSubrangeMethodArgumentResolver(resolvers);
		addSortMethodArgumentResolver(resolvers);
		if (springSecurityPresent) {
			ApplicationContext context = obtainApplicationContext();
			resolvers.addResolver(new PrincipalMethodArgumentResolver());
			resolvers.addResolver(new AuthenticationPrincipalArgumentResolver(new BeanFactoryResolver(context)));
		}
		if (KotlinDetector.isKotlinPresent()) {
			resolvers.addResolver(new ContinuationHandlerMethodArgumentResolver());
		}

		this.customArgumentResolvers.forEach(resolvers::addResolver);

		// This works as a fallback, after all other resolvers
		resolvers.addResolver(new SourceMethodArgumentResolver());

		return resolvers;
	}

	@SuppressWarnings({"unchecked", "CastCanBeRemovedNarrowingVariableType"})
	private void addSubrangeMethodArgumentResolver(HandlerMethodArgumentResolverComposite resolvers) {
		try {
			CursorStrategy<?> strategy = obtainApplicationContext().getBean(CursorStrategy.class);
			if (springDataPresent) {
				if (strategy.supports(ScrollPosition.class)) {
					CursorStrategy<ScrollPosition> strategyToUse = (CursorStrategy<ScrollPosition>) strategy;
					resolvers.addResolver(new ScrollSubrangeMethodArgumentResolver(strategyToUse));
					return;
				}
			}
			resolvers.addResolver(new SubrangeMethodArgumentResolver<>(strategy));
		}
		catch (NoSuchBeanDefinitionException ex) {
			// ignore
		}
	}

	private void addSortMethodArgumentResolver(HandlerMethodArgumentResolverComposite resolvers) {
		if (springDataPresent) {
			try {
				SortStrategy strategy = obtainApplicationContext().getBean(SortStrategy.class);
				resolvers.addResolver(new SortMethodArgumentResolver(strategy));
			}
			catch (NoSuchBeanDefinitionException ex) {
				// ignore
			}
		}
	}


	@Override
	public void configure(RuntimeWiring.Builder runtimeWiringBuilder) {
		detectHandlerMethods().forEach(info -> {
			DataFetcher<?> dataFetcher;
			if (!info.isBatchMapping()) {
				dataFetcher = new SchemaMappingDataFetcher(
						info, getArgumentResolvers(), this.validationHelper, getExceptionResolver(),
						getExecutor(), getContextSnapshotFactory());
			}
			else {
				dataFetcher = registerBatchLoader(info);
			}
			FieldCoordinates coordinates = info.getCoordinates();
			runtimeWiringBuilder.type(coordinates.getTypeName(), typeBuilder ->
					typeBuilder.dataFetcher(coordinates.getFieldName(), dataFetcher));
		});
	}

	@Override
	protected DataFetcherMappingInfo getMappingInfo(Method method, Object handler, Class<?> handlerType) {
		Set<Annotation> annotations = AnnotatedElementUtils.findAllMergedAnnotations(
				method, new LinkedHashSet<>(Arrays.asList(BatchMapping.class, SchemaMapping.class)));

		if (annotations.isEmpty()) {
			return null;
		}

		if (annotations.size() != 1) {
			throw new IllegalArgumentException(
					"Expected either @BatchMapping or @SchemaMapping, not both: " + method.toGenericString());
		}

		String typeName;
		String field;
		boolean batchMapping = false;
		int batchSize = -1;
		HandlerMethod handlerMethod = createHandlerMethod(method, handler, handlerType);

		Annotation annotation = annotations.iterator().next();
		if (annotation instanceof SchemaMapping mapping) {
			typeName = mapping.typeName();
			field = (StringUtils.hasText(mapping.field()) ? mapping.field() : method.getName());
		}
		else {
			BatchMapping mapping = (BatchMapping) annotation;
			typeName = mapping.typeName();
			field = (StringUtils.hasText(mapping.field()) ? mapping.field() : method.getName());
			batchMapping = true;
			batchSize = mapping.maxBatchSize();
		}

		if (!StringUtils.hasText(typeName)) {
			SchemaMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handlerType, SchemaMapping.class);
			if (mapping != null) {
				typeName = mapping.typeName();
			}
		}

		if (!StringUtils.hasText(typeName)) {
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (!batchMapping) {
					HandlerMethodArgumentResolver resolver = getArgumentResolvers().getArgumentResolver(parameter);
					if (resolver instanceof SourceMethodArgumentResolver) {
						typeName = parameter.getParameterType().getSimpleName();
						break;
					}
				}
				else {
					if (Collection.class.isAssignableFrom(parameter.getParameterType())) {
						typeName = parameter.nested().getNestedParameterType().getSimpleName();
						break;
					}
				}
			}
		}

		Assert.hasText(typeName,
				"No parentType specified, and a source/parent method argument was also not found: " +
						handlerMethod.getShortLogMessage());

		return new DataFetcherMappingInfo(typeName, field, batchMapping, batchSize, handlerMethod);
	}

	@Override
	protected HandlerMethod getHandlerMethod(DataFetcherMappingInfo mappingInfo) {
		return mappingInfo.getHandlerMethod();
	}

	private DataFetcher<Object> registerBatchLoader(DataFetcherMappingInfo info) {
		if (!info.isBatchMapping()) {
			throw new IllegalArgumentException("Not a @BatchMapping method: " + info);
		}

		String dataLoaderKey = info.getCoordinates().toString();
		BatchLoaderRegistry registry = obtainApplicationContext().getBean(BatchLoaderRegistry.class);
		BatchLoaderRegistry.RegistrationSpec<Object, Object> registration = registry.forName(dataLoaderKey);
		if (info.getMaxBatchSize() > 0) {
			registration.withOptions(options -> options.setMaxBatchSize(info.getMaxBatchSize()));
		}

		HandlerMethod handlerMethod = info.getHandlerMethod();
		BatchLoaderHandlerMethod invocable =
				new BatchLoaderHandlerMethod(handlerMethod, getExecutor(), getContextSnapshotFactory());

		MethodParameter returnType = handlerMethod.getReturnType();
		Class<?> clazz = returnType.getParameterType();

		if (clazz.equals(Callable.class)) {
			returnType = returnType.nested();
			clazz = returnType.getNestedParameterType();
		}

		if (clazz.equals(Flux.class) || Collection.class.isAssignableFrom(clazz)) {
			registration.registerBatchLoader(invocable::invokeForIterable);
			ResolvableType valueType = ResolvableType.forMethodParameter(returnType.nested());
			return new BatchMappingDataFetcher(info, valueType, dataLoaderKey);
		}

		if (clazz.equals(Mono.class)) {
			returnType = returnType.nested();
			clazz = returnType.getNestedParameterType();
		}

		if (Map.class.isAssignableFrom(clazz)) {
			registration.registerMappedBatchLoader(invocable::invokeForMap);
			ResolvableType valueType = ResolvableType.forMethodParameter(returnType.nested(1));
			return new BatchMappingDataFetcher(info, valueType, dataLoaderKey);
		}

		throw new IllegalStateException(
				"@BatchMapping method is expected to return " +
						"Mono<Map<K, V>>, Map<K, V>, Flux<V>, or Collection<V>: " + handlerMethod);
	}

	/**
	 * Alternative to {@link #configure(RuntimeWiring.Builder)} that registers
	 * data fetchers in a {@link GraphQLCodeRegistry.Builder}. This could be
	 * used with programmatic creation of {@link graphql.schema.GraphQLSchema}.
	 */
	@SuppressWarnings("rawtypes")
	public void configure(GraphQLCodeRegistry.Builder codeRegistryBuilder) {

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		configure(wiringBuilder);
		RuntimeWiring runtimeWiring = wiringBuilder.build();

		runtimeWiring.getDataFetchers().forEach((typeName, dataFetcherMap) ->
				dataFetcherMap.forEach((key, value) -> {
					FieldCoordinates coordinates = FieldCoordinates.coordinates(typeName, key);
					codeRegistryBuilder.dataFetcher(coordinates, (DataFetcher<?>) value);
				}));
	}


	/**
	 * {@link DataFetcher} that wrap and invokes a {@link HandlerMethod}.
	 */
	static class SchemaMappingDataFetcher implements SelfDescribingDataFetcher<Object> {

		private final DataFetcherMappingInfo mappingInfo;

		private final HandlerMethodArgumentResolverComposite argumentResolvers;

		@Nullable
		private final BiConsumer<Object, Object[]> methodValidationHelper;

		private final HandlerDataFetcherExceptionResolver exceptionResolver;

		@Nullable
		private final Executor executor;

		@Nullable final ContextSnapshotFactory snapshotFactory;

		private final boolean subscription;

		SchemaMappingDataFetcher(
				DataFetcherMappingInfo info, HandlerMethodArgumentResolverComposite argumentResolvers,
				@Nullable ValidationHelper helper, HandlerDataFetcherExceptionResolver exceptionResolver,
				@Nullable Executor executor, @Nullable ContextSnapshotFactory snapshotFactory) {

			this.mappingInfo = info;
			this.argumentResolvers = argumentResolvers;
			this.snapshotFactory = snapshotFactory;

			this.methodValidationHelper =
					(helper != null ? helper.getValidationHelperFor(info.getHandlerMethod()) : null);

			this.exceptionResolver = exceptionResolver;

			this.executor = executor;
			this.subscription = this.mappingInfo.getCoordinates().getTypeName().equalsIgnoreCase("Subscription");
		}

		@Override
		public String getDescription() {
			return this.mappingInfo.getHandlerMethod().getShortLogMessage();
		}

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forMethodReturnType(this.mappingInfo.getHandlerMethod().getMethod());
		}

		/**
		 * Return the {@link HandlerMethod} used to fetch data.
		 */
		public HandlerMethod getHandlerMethod() {
			return this.mappingInfo.getHandlerMethod();
		}

		@Override
		@SuppressWarnings({"ConstantConditions", "ReactiveStreamsUnusedPublisher"})
		public Object get(DataFetchingEnvironment environment) throws Exception {

			DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
					getHandlerMethod(), this.argumentResolvers, this.methodValidationHelper,
					this.subscription, this.executor, this.snapshotFactory);

			try {
				Object result = handlerMethod.invoke(environment);
				return applyExceptionHandling(environment, handlerMethod, result);
			}
			catch (Throwable ex) {
				return handleException(ex, environment, handlerMethod);
			}
		}

		@SuppressWarnings({"unchecked", "ReactiveStreamsUnusedPublisher"})
		private <T> Object applyExceptionHandling(
				DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod, Object result) {

			if (this.subscription && result instanceof Publisher<?> publisher) {
				result = Flux.from(publisher).onErrorResume(ex -> handleSubscriptionError(ex, env, handlerMethod));
			}
			else if (result instanceof Mono) {
				result = ((Mono<T>) result).onErrorResume(ex -> (Mono<T>) handleException(ex, env, handlerMethod));
			}
			else if (result instanceof Flux<?>) {
				result = ((Flux<T>) result).onErrorResume(ex -> (Mono<T>) handleException(ex, env, handlerMethod));
			}
			return result;
		}

		private Mono<DataFetcherResult<Object>> handleException(
				Throwable ex, DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod) {

			return this.exceptionResolver.resolveException(ex, env, handlerMethod.getBean())
					.map(errors -> DataFetcherResult.newResult().errors(errors).build())
					.switchIfEmpty(Mono.error(ex));
		}

		@SuppressWarnings("unchecked")
		private <T> Publisher<T> handleSubscriptionError(
				Throwable ex, DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod) {

			return (Publisher<T>) this.exceptionResolver.resolveException(ex, env, handlerMethod.getBean())
					.flatMap(errors -> Mono.error(new SubscriptionPublisherException(errors, ex)))
					.switchIfEmpty(Mono.error(ex));
		}

		@Override
		public String toString() {
			return getDescription();
		}

	}


	static class BatchMappingDataFetcher implements DataFetcher<Object>, SelfDescribingDataFetcher<Object> {

		private final DataFetcherMappingInfo mappingInfo;

		private final ResolvableType returnType;

		private final String dataLoaderKey;

		BatchMappingDataFetcher(DataFetcherMappingInfo info, ResolvableType valueType, String dataLoaderKey) {
			this.mappingInfo = info;
			this.returnType = ResolvableType.forClassWithGenerics(CompletableFuture.class, valueType);
			this.dataLoaderKey = dataLoaderKey;
		}

		@Override
		public String getDescription() {
			return "@BatchMapping " + this.mappingInfo.getHandlerMethod().getShortLogMessage();
		}

		@Override
		public ResolvableType getReturnType() {
			return this.returnType;
		}

		@Override
		public Object get(DataFetchingEnvironment env) {
			DataLoader<?, ?> dataLoader = env.getDataLoaderRegistry().getDataLoader(this.dataLoaderKey);
			Assert.state(dataLoader != null, "No DataLoader for key '" + this.dataLoaderKey + "'");
			return dataLoader.load(env.getSource());
		}
	}

}
