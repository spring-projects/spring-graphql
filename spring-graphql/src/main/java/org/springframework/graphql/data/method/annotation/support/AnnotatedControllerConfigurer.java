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
package org.springframework.graphql.data.method.annotation.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.RuntimeWiring;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataloader.DataLoader;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.query.SortStrategy;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
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
 * annotated handler methods in {@link Controller @Controller} classes declared in
 * Spring configuration, and registers them as {@link DataFetcher}s.
 *
 * <p>In addition to initializing a {@link RuntimeWiring.Builder}, this class, also
 * provides an option to {@link #configure(GraphQLCodeRegistry.Builder) configure}
 * data fetchers on a {@link GraphQLCodeRegistry.Builder}.
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
 *
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class AnnotatedControllerConfigurer implements ApplicationContextAware, InitializingBean, RuntimeWiringConfigurer {

	private static final ClassLoader classLoader = AnnotatedControllerConfigurer.class.getClassLoader();

	private static final boolean springDataPresent = ClassUtils.isPresent(
			"org.springframework.data.projection.SpelAwareProxyProjectionFactory", classLoader);

	private static final boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext", classLoader);

	private static final boolean beanValidationPresent = ClassUtils.isPresent(
			"jakarta.validation.executable.ExecutableValidator", classLoader);


	private static final Log logger = LogFactory.getLog(AnnotatedControllerConfigurer.class);

	/**
	 * Bean name prefix for target beans behind scoped proxies. Used to exclude those
	 * targets from handler method detection, in favor of the corresponding proxies.
	 * <p>We're not checking the autowire-candidate status here, which is how the
	 * proxy target filtering problem is being handled at the autowiring level,
	 * since autowire-candidate may have been turned to {@code false} for other
	 * reasons, while still expecting the bean to be eligible for handler methods.
	 * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
	 * but duplicated here to avoid a hard dependency on the spring-aop module.
	 */
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";


	private final FormattingConversionService conversionService = new DefaultFormattingConversionService();

	private boolean fallBackOnDirectFieldAccess;

	private final List<HandlerMethodArgumentResolver> customArgumentResolvers = new ArrayList<>(8);

	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	@Nullable
	private ValidationHelper validationHelper;

	@Nullable
	private AnnotatedControllerExceptionResolver exceptionResolver;

	@Nullable
	private Executor executor;

	@Nullable
	private ApplicationContext applicationContext;


	/**
	 * Add a {@code FormatterRegistrar} to customize the {@link ConversionService}
	 * that assists in binding GraphQL arguments onto
	 * {@link org.springframework.graphql.data.method.annotation.Argument @Argument}
	 * annotated method parameters.
	 */
	public void addFormatterRegistrar(FormatterRegistrar registrar) {
		registrar.registerFormatters(this.conversionService);
	}

	/**
	 * Whether binding GraphQL arguments onto
	 * {@link org.springframework.graphql.data.method.annotation.Argument @Argument}
	 * should falls back to direct field access in case the target object does
	 * not use accessor methods.
	 * @since 1.2.0
	 */
	public void setFallBackOnDirectFieldAccess(boolean fallBackOnDirectFieldAccess) {
		this.fallBackOnDirectFieldAccess = fallBackOnDirectFieldAccess;
	}

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

	HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		Assert.notNull(this.argumentResolvers,
				"HandlerMethodArgumentResolverComposite is not yet initialized, was afterPropertiesSet called?");
		return this.argumentResolvers;
	}

	/**
	 * Return a {@link DataFetcherExceptionResolver} that resolves exceptions with
	 * {@code @GraphQlExceptionHandler} methods in {@code @ControllerAdvice}
	 * classes declared in Spring configuration. This is useful primarily for
	 * exceptions from non-controller {@link DataFetcher}s since exceptions from
	 * {@code @SchemaMapping} controller methods are handled automatically at
	 * the point of invocation.
	 *
	 * @return a resolver instance that can be plugged into
	 * {@link org.springframework.graphql.execution.GraphQlSource.Builder#exceptionResolvers(List)
	 * GraphQlSource.Builder}
	 *
	 * @since 1.2.0
	 */
	public DataFetcherExceptionResolver getExceptionResolver() {
		Assert.notNull(this.exceptionResolver,
				"DataFetcherExceptionResolver is not yet initialized, was afterPropertiesSet called?");
		return (ex, env) -> this.exceptionResolver.resolveException(ex, env, null);
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

	/**
	 * Configure an {@link Executor} to use for asynchronous handling of
	 * {@link Callable} return values from controller methods.
	 * <p>By default, this is not set in which case controller methods with a
	 * {@code Callable} return value cannot be registered.
	 * @param executor the executor to use
	 */
	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}


	@Override
	public void afterPropertiesSet() {

		this.argumentResolvers = initArgumentResolvers();

		this.exceptionResolver = new AnnotatedControllerExceptionResolver(this.argumentResolvers);
		if (this.applicationContext != null) {
			this.exceptionResolver.registerControllerAdvice(this.applicationContext);
		}

		if (beanValidationPresent) {
			this.validationHelper = ValidationHelper.createIfValidatorPresent(obtainApplicationContext());
		}
	}

	private HandlerMethodArgumentResolverComposite initArgumentResolvers() {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();

		// Annotation based
		if (springDataPresent) {
			// Must be ahead of ArgumentMethodArgumentResolver
			resolvers.addResolver(new ProjectedPayloadMethodArgumentResolver(obtainApplicationContext()));
		}

		GraphQlArgumentBinder argumentBinder =
				new GraphQlArgumentBinder(this.conversionService, this.fallBackOnDirectFieldAccess);

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

	protected final ApplicationContext obtainApplicationContext() {
		Assert.state(this.applicationContext != null, "No ApplicationContext");
		return this.applicationContext;
	}


	@Override
	public void configure(RuntimeWiring.Builder runtimeWiringBuilder) {
		Assert.state(this.argumentResolvers != null, "`argumentResolvers` is not initialized");
		Assert.state(this.exceptionResolver != null, "`exceptionResolver` is not initialized");

		findHandlerMethods().forEach(info -> {
			DataFetcher<?> dataFetcher;
			if (!info.isBatchMapping()) {
				dataFetcher = new SchemaMappingDataFetcher(
						info, this.argumentResolvers, this.validationHelper, this.exceptionResolver, this.executor);
			}
			else {
				dataFetcher = registerBatchLoader(info);
			}
			runtimeWiringBuilder.type(info.getCoordinates().getTypeName(), typeBuilder ->
					typeBuilder.dataFetcher(info.getCoordinates().getFieldName(), dataFetcher));
		});
	}

	/**
	 * Scan beans in the ApplicationContext, detect and prepare a map of handler methods.
	 */
	private Collection<MappingInfo> findHandlerMethods() {
		ApplicationContext context = obtainApplicationContext();
		Map<FieldCoordinates, MappingInfo> result = new HashMap<>();
		for (String beanName : context.getBeanNamesForType(Object.class)) {
			if (beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				continue;
			}
			Class<?> beanType = null;
			try {
				beanType = context.getType(beanName);
			}
			catch (Throwable ex) {
				// An unresolvable bean type, probably from a lazy bean - let's ignore it.
				if (logger.isTraceEnabled()) {
					logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
				}
			}
			if (beanType == null || !AnnotatedElementUtils.hasAnnotation(beanType, Controller.class)) {
				continue;
			}
			Class<?> beanClass = context.getType(beanName);
			findHandlerMethods(beanName, beanClass).forEach(info -> {
				HandlerMethod handlerMethod = info.getHandlerMethod();
				MappingInfo existing = result.put(info.getCoordinates(), info);
				if (existing != null && !existing.getHandlerMethod().equals(handlerMethod)) {
					throw new IllegalStateException(
							"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
									handlerMethod + "\nto " + info.getCoordinates() + ": There is already '" +
									existing.getHandlerMethod().getBean() + "' bean method\n" + existing + " mapped.");
				}
			});
		}
		return result.values();
	}

	private Collection<MappingInfo> findHandlerMethods(Object handler, @Nullable Class<?> handlerClass) {
		if (handlerClass == null) {
			return Collections.emptyList();
		}

		Class<?> userClass = ClassUtils.getUserClass(handlerClass);
		Map<Method, MappingInfo> map = MethodIntrospector.selectMethods(
				userClass, (Method method) -> getMappingInfo(method, handler, userClass));

		Collection<MappingInfo> mappingInfos = map.values();

		if (logger.isTraceEnabled() && !mappingInfos.isEmpty()) {
			logger.trace(formatMappings(userClass, mappingInfos));
		}

		return mappingInfos;
	}

	@Nullable
	private MappingInfo getMappingInfo(Method method, Object handler, Class<?> handlerType) {

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
			field = StringUtils.hasText(mapping.field()) ? mapping.field() : method.getName();
		}
		else {
			BatchMapping mapping = (BatchMapping) annotation;
			typeName = mapping.typeName();
			field = StringUtils.hasText(mapping.field()) ? mapping.field() : method.getName();
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
					Assert.state(this.argumentResolvers != null, "`argumentResolvers` is not initialized");
					HandlerMethodArgumentResolver resolver = this.argumentResolvers.getArgumentResolver(parameter);
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

		return new MappingInfo(typeName, field, batchMapping, batchSize, handlerMethod);
	}

	private HandlerMethod createHandlerMethod(Method method, Object handler, Class<?> handlerType) {
		Method theMethod = AopUtils.selectInvocableMethod(method, handlerType);
		return handler instanceof String ?
				new HandlerMethod((String) handler, obtainApplicationContext().getAutowireCapableBeanFactory(), theMethod) :
				new HandlerMethod(handler, theMethod);
	}

	private String formatMappings(Class<?> handlerType, Collection<MappingInfo> infos) {
		String formattedType = Arrays.stream(ClassUtils.getPackageName(handlerType).split("\\."))
				.map(p -> p.substring(0, 1))
				.collect(Collectors.joining(".", "", "." + handlerType.getSimpleName()));
		return infos.stream()
				.map(mappingInfo -> {
					Method method = mappingInfo.getHandlerMethod().getMethod();
					String methodParameters = Arrays.stream(method.getGenericParameterTypes())
							.map(Type::getTypeName)
							.collect(Collectors.joining(",", "(", ")"));
					return mappingInfo.getCoordinates() + " => " + method.getName() + methodParameters;
				})
				.collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
	}

	private DataFetcher<Object> registerBatchLoader(MappingInfo info) {
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
		BatchLoaderHandlerMethod invocable = new BatchLoaderHandlerMethod(handlerMethod, this.executor);

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


	private static class MappingInfo {

		private final FieldCoordinates coordinates;

		private final boolean batchMapping;

		private final int maxBatchSize;

		private final HandlerMethod handlerMethod;

		public MappingInfo(
				String typeName, String field, boolean batchMapping, int maxBatchSize,
				HandlerMethod handlerMethod) {

			this.coordinates = FieldCoordinates.coordinates(typeName, field);
			this.batchMapping = batchMapping;
			this.maxBatchSize = maxBatchSize;
			this.handlerMethod = handlerMethod;
		}

		public FieldCoordinates getCoordinates() {
			return this.coordinates;
		}

		@SuppressWarnings("BooleanMethodIsAlwaysInverted")
		public boolean isBatchMapping() {
			return this.batchMapping;
		}

		public int getMaxBatchSize() {
			return this.maxBatchSize;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		@Override
		public String toString() {
			return this.coordinates + " -> " + this.handlerMethod;
		}
	}


	/**
	 * {@link DataFetcher} that wrap and invokes a {@link HandlerMethod}.
	 */
	static class SchemaMappingDataFetcher implements SelfDescribingDataFetcher<Object> {

		private final MappingInfo info;

		private final HandlerMethodArgumentResolverComposite argumentResolvers;

		@Nullable
		private final BiConsumer<Object, Object[]> methodValidationHelper;

		private final AnnotatedControllerExceptionResolver exceptionResolver;

		@Nullable
		private final Executor executor;

		private final boolean subscription;

		SchemaMappingDataFetcher(
				MappingInfo info, HandlerMethodArgumentResolverComposite argumentResolvers,
				@Nullable ValidationHelper helper, AnnotatedControllerExceptionResolver exceptionResolver,
				@Nullable Executor executor) {

			this.info = info;
			this.argumentResolvers = argumentResolvers;

			this.methodValidationHelper =
					helper != null ? helper.getValidationHelperFor(info.getHandlerMethod()) : null;

			// Register controllers early to validate exception handler return types
			Class<?> controllerType = info.getHandlerMethod().getBeanType();
			exceptionResolver.registerController(controllerType);

			this.exceptionResolver = exceptionResolver;

			this.executor = executor;
			this.subscription = "Subscription".equalsIgnoreCase(this.info.getCoordinates().getTypeName());
		}

		@Override
		public String getDescription() {
			return this.info.getHandlerMethod().getShortLogMessage();
		}

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forMethodReturnType(this.info.getHandlerMethod().getMethod());
		}

		/**
		 * Return the {@link HandlerMethod} used to fetch data.
		 */
		public HandlerMethod getHandlerMethod() {
			return this.info.getHandlerMethod();
		}

		@Override
		@SuppressWarnings({"ConstantConditions", "ReactiveStreamsUnusedPublisher"})
		public Object get(DataFetchingEnvironment environment) throws Exception {

			DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
					getHandlerMethod(), this.argumentResolvers, this.methodValidationHelper,
					this.executor, this.subscription);

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

		private final MappingInfo info;

		private final ResolvableType returnType;

		private final String dataLoaderKey;

		BatchMappingDataFetcher(MappingInfo info, ResolvableType valueType, String dataLoaderKey) {
			this.info = info;
			this.returnType = ResolvableType.forClassWithGenerics(CompletableFuture.class, valueType);
			this.dataLoaderKey = dataLoaderKey;
		}

		@Override
		public String getDescription() {
			return "@BatchMapping " + this.info.getHandlerMethod().getShortLogMessage();
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
