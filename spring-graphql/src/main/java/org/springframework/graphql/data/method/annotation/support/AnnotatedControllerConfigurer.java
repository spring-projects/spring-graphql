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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import graphql.execution.DataFetcherResult;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoader;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.query.SortStrategy;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.ReactiveAdapterRegistryHelper;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

	private static final boolean springDataPresent = ClassUtils.isPresent(
			"org.springframework.data.projection.SpelAwareProxyProjectionFactory", classLoader);

	private static final boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext", classLoader);

	private static final boolean beanValidationPresent = ClassUtils.isPresent(
			"jakarta.validation.executable.ExecutableValidator", classLoader);


	private final List<HandlerMethodArgumentResolver> customArgumentResolvers = new ArrayList<>(8);

	private final InterfaceMappingHelper interfaceMappingHelper = new InterfaceMappingHelper();

	@Nullable
	private ValidationHelper validationHelper;


	/**
	 * Add a {@link HandlerMethodArgumentResolver} for custom controller method
	 * arguments. Such custom resolvers are ordered after built-in resolvers
	 * except for {@link SourceMethodArgumentResolver}, which is always last.
	 * @param resolver the resolver to add.
	 * @since 1.2.0
	 */
	public void addCustomArgumentResolver(HandlerMethodArgumentResolver resolver) {
		this.customArgumentResolvers.add(resolver);
	}

	@Override
	public void setTypeDefinitionRegistry(TypeDefinitionRegistry registry) {
		this.interfaceMappingHelper.setTypeDefinitionRegistry(registry);
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
		if (springSecurityPresent) {
			ApplicationContext context = obtainApplicationContext();
			resolvers.addResolver(new AuthenticationPrincipalArgumentResolver(new BeanFactoryResolver(context)));
		}

		// Type based
		resolvers.addResolver(new DataFetchingEnvironmentMethodArgumentResolver());
		resolvers.addResolver(new DataLoaderMethodArgumentResolver());
		addSubrangeMethodArgumentResolver(resolvers);
		addSortMethodArgumentResolver(resolvers);
		if (springSecurityPresent) {
			resolvers.addResolver(new PrincipalMethodArgumentResolver());
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

		Set<DataFetcherMappingInfo> allInfos = detectHandlerMethods();
		Set<DataFetcherMappingInfo> subTypeInfos = this.interfaceMappingHelper.removeInterfaceMappings(allInfos);

		allInfos.forEach((info) -> registerDataFetcher(info, runtimeWiringBuilder));

		RuntimeWiring wiring = runtimeWiringBuilder.build();
		subTypeInfos = this.interfaceMappingHelper.removeExplicitMappings(subTypeInfos, wiring.getDataFetchers());

		subTypeInfos.forEach((info) -> registerDataFetcher(info, runtimeWiringBuilder));

		if (logger.isTraceEnabled()) {
			logger.trace("Controller method registrations:" + formatRegistrations(runtimeWiringBuilder));
		}
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
						Class<?> type = parameter.nested().getNestedParameterType();
						if (Object.class.equals(type)) {
							// Maybe a Kotlin List
							type = ResolvableType.forMethodParameter(parameter).getNested(2).resolve(Object.class);
						}
						if (!Object.class.equals(type)) {
							typeName = type.getSimpleName();
						}
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

	private void registerDataFetcher(DataFetcherMappingInfo info, RuntimeWiring.Builder runtimeWiringBuilder) {
		DataFetcher<?> dataFetcher;
		if (!info.isBatchMapping()) {
			dataFetcher = new SchemaMappingDataFetcher(
					info, getArgumentResolvers(), this.validationHelper, getExceptionResolver(),
					getExecutor(), shouldInvokeAsync(info.getHandlerMethod()));
		}
		else {
			dataFetcher = registerBatchLoader(info);
		}
		FieldCoordinates coordinates = info.getCoordinates();
		runtimeWiringBuilder.type(coordinates.getTypeName(), (typeBuilder) ->
				typeBuilder.dataFetcher(coordinates.getFieldName(), dataFetcher));
	}

	private DataFetcher<Object> registerBatchLoader(DataFetcherMappingInfo info) {
		if (!info.isBatchMapping()) {
			throw new IllegalArgumentException("Not a @BatchMapping method: " + info);
		}

		String dataLoaderKey = info.getCoordinates().toString();
		BatchLoaderRegistry registry = obtainApplicationContext().getBean(BatchLoaderRegistry.class);
		BatchLoaderRegistry.RegistrationSpec<Object, Object> registration = registry.forName(dataLoaderKey);
		if (info.getMaxBatchSize() > 0) {
			registration.withOptions((options) -> options.setMaxBatchSize(info.getMaxBatchSize()));
		}

		HandlerMethod handlerMethod = info.getHandlerMethod();
		BatchLoaderHandlerMethod invocable =
				new BatchLoaderHandlerMethod(handlerMethod, getExecutor(), shouldInvokeAsync(handlerMethod));

		MethodParameter returnType = handlerMethod.getReturnType();
		Class<?> clazz = returnType.getParameterType();
		Method method = handlerMethod.getMethod();

		if (clazz.equals(Callable.class)) {
			returnType = returnType.nested();
			clazz = returnType.getNestedParameterType();
		}

		ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(clazz);

		if (Collection.class.isAssignableFrom(clazz) || (adapter != null && adapter.isMultiValue())) {
			registration.registerBatchLoader(invocable::invokeForIterable);
			ResolvableType valueType = ResolvableType.forMethodParameter(returnType.nested());
			return new BatchMappingDataFetcher(info, valueType, dataLoaderKey);
		}

		if (adapter != null) {
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

	@SuppressWarnings("rawtypes")
	protected static String formatRegistrations(RuntimeWiring.Builder wiringBuilder) {
		return wiringBuilder.build().getDataFetchers().entrySet().stream()
				.map((typeEntry) -> typeEntry.getKey() + ":\n" +
						typeEntry.getValue().entrySet().stream()
								.map((fieldEntry) -> fieldEntry.getKey() + " -> " + fieldEntry.getValue())
								.collect(Collectors.joining("\n\t", "\t", "")))
				.collect(Collectors.joining("\n", "\n", "\n"));
	}

	/**
	 * Alternative to {@link #configure(RuntimeWiring.Builder)} that registers
	 * data fetchers in a {@link GraphQLCodeRegistry.Builder}. This could be
	 * used with programmatic creation of {@link graphql.schema.GraphQLSchema}.
	 * @param codeRegistryBuilder the code registry
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

		private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

		private final DataFetcherMappingInfo mappingInfo;

		private final HandlerMethodArgumentResolverComposite argumentResolvers;

		@Nullable
		private final BiConsumer<Object, Object[]> methodValidationHelper;

		private final HandlerDataFetcherExceptionResolver exceptionResolver;

		@Nullable
		private final Executor executor;

		private final boolean invokeAsync;

		private final boolean subscription;

		SchemaMappingDataFetcher(
				DataFetcherMappingInfo info, HandlerMethodArgumentResolverComposite argumentResolvers,
				@Nullable ValidationHelper helper, HandlerDataFetcherExceptionResolver exceptionResolver,
				@Nullable Executor executor, boolean invokeAsync) {

			this.mappingInfo = info;
			this.argumentResolvers = argumentResolvers;

			this.methodValidationHelper =
					(helper != null) ? helper.getValidationHelperFor(info.getHandlerMethod()) : null;

			this.exceptionResolver = exceptionResolver;

			this.executor = executor;
			this.invokeAsync = invokeAsync;
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

		@Override
		public Map<String, ResolvableType> getArguments() {

			Predicate<MethodParameter> argumentPredicate = (p) ->
					(p.getParameterAnnotation(Argument.class) != null || p.getParameterType() == ArgumentValue.class);

			return Arrays.stream(this.mappingInfo.getHandlerMethod().getMethodParameters())
					.filter(argumentPredicate)
					.peek((p) -> p.initParameterNameDiscovery(parameterNameDiscoverer))
					.collect(Collectors.toMap(
							ArgumentMethodArgumentResolver::getArgumentName,
							ResolvableType::forMethodParameter));
		}

		/**
		 * Return the {@link HandlerMethod} used to fetch data.
		 */
		HandlerMethod getHandlerMethod() {
			return this.mappingInfo.getHandlerMethod();
		}

		@Override
		@SuppressWarnings({"ConstantConditions", "ReactiveStreamsUnusedPublisher"})
		public Object get(DataFetchingEnvironment environment) throws Exception {

			DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
					getHandlerMethod(), this.argumentResolvers, this.methodValidationHelper,
					this.executor, this.invokeAsync, this.subscription);

			try {
				Object result = handlerMethod.invoke(environment);
				return applyExceptionHandling(environment, handlerMethod, result);
			}
			catch (Throwable ex) {
				return handleException(ex, environment, handlerMethod);
			}
		}

		@SuppressWarnings({"unchecked", "ReactiveStreamsUnusedPublisher"})
		@Nullable
		private <T> Object applyExceptionHandling(
				DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod, Object result) {

			if (this.subscription) {
				return ReactiveAdapterRegistryHelper.toSubscriptionFlux(result)
						.onErrorResume((ex) -> handleSubscriptionError(ex, env, handlerMethod));
			}

			result = ReactiveAdapterRegistryHelper.toMonoOrFluxIfReactive(result);

			if (result instanceof Mono) {
				result = ((Mono<T>) result).onErrorResume((ex) -> (Mono<T>) handleException(ex, env, handlerMethod));
			}
			else if (result instanceof Flux<?>) {
				result = ((Flux<T>) result).onErrorResume((ex) -> (Mono<T>) handleException(ex, env, handlerMethod));
			}

			return result;
		}

		private Mono<DataFetcherResult<Object>> handleException(
				Throwable ex, DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod) {

			return this.exceptionResolver.resolveException(ex, env, handlerMethod.getBean())
					.map((errors) -> DataFetcherResult.newResult().errors(errors).build())
					.switchIfEmpty(Mono.error(ex));
		}

		@SuppressWarnings("unchecked")
		private <T> Publisher<T> handleSubscriptionError(
				Throwable ex, DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod) {

			return (Publisher<T>) this.exceptionResolver.resolveException(ex, env, handlerMethod.getBean())
					.flatMap((errors) -> Mono.error(new SubscriptionPublisherException(errors, ex)))
					.switchIfEmpty(Mono.error(ex));
		}

		@Override
		public String toString() {
			return getDescription();
		}

	}


	/**
	 * {@link DataFetcher} that uses a DataLoader.
	 */
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
			return dataLoader.load(env.getSource(), (env.getLocalContext() != null) ? env.getLocalContext() : env.getGraphQlContext());
		}

		@Override
		public String toString() {
			return getDescription();
		}
	}


	/**
	 * Helper to expand schema interface mappings into object type mappings.
	 */
	private static final class InterfaceMappingHelper {

		private final MultiValueMap<String, String> interfaceMappings = new LinkedMultiValueMap<>();

		/**
		 * Extract information interface implementation types.
		 */
		void setTypeDefinitionRegistry(TypeDefinitionRegistry registry) {
			for (TypeDefinition<?> definition : registry.types().values()) {
				if (definition instanceof ObjectTypeDefinition objectDefinition) {
					for (Type<?> type : objectDefinition.getImplements()) {
						this.interfaceMappings.add(((TypeName) type).getName(), objectDefinition.getName());
					}
				}
			}
		}

		/**
		 * Remove mappings to interface fields, and return mappings for the same
		 * fields in all implementing types.
		 */
		Set<DataFetcherMappingInfo> removeInterfaceMappings(Set<DataFetcherMappingInfo> infos) {
			Set<DataFetcherMappingInfo> subTypeMappings = new LinkedHashSet<>();
			Iterator<DataFetcherMappingInfo> it = infos.iterator();
			while (it.hasNext()) {
				DataFetcherMappingInfo info = it.next();
				List<String> names = this.interfaceMappings.get(info.getTypeName());
				if (names != null) {
					for (String name : names) {
						subTypeMappings.add(new DataFetcherMappingInfo(name, info));
					}
					it.remove();
				}
			}
			return subTypeMappings;
		}

		/**
		 * Remove mappings that are covered by explicit {@link DataFetcher} registrations.
		 */
		@SuppressWarnings("rawtypes")
		Set<DataFetcherMappingInfo> removeExplicitMappings(
				Set<DataFetcherMappingInfo> infos, Map<String, Map<String, DataFetcher>> dataFetchers) {

			return infos.stream()
					.filter((info) -> {
						Map<String, DataFetcher> registrations = dataFetchers.get(info.getTypeName());
						return (registrations == null || !registrations.containsKey(info.getFieldName()));
					})
					.collect(Collectors.toSet());
		}
	}

}
