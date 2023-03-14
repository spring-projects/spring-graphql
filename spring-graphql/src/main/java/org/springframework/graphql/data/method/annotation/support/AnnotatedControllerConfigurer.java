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
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import graphql.com.google.common.collect.Iterables;
import graphql.execution.DataFetcherResult;
import graphql.schema.*;
import graphql.schema.idl.RuntimeWiring;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataloader.DataLoader;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.BeanResolver;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.TypedDataFetcher;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder;

/**
 * {@link RuntimeWiringConfigurer} that detects {@link SchemaMapping @SchemaMapping}
 * annotated handler methods in {@link Controller @Controller} classes and
 * registers them as {@link DataFetcher}s.
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
		implements ApplicationContextAware, InitializingBean, RuntimeWiringConfigurer {

	private final static Log logger = LogFactory.getLog(AnnotatedControllerConfigurer.class);

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

	private final static boolean springDataPresent = ClassUtils.isPresent(
			"org.springframework.data.projection.SpelAwareProxyProjectionFactory",
			AnnotatedControllerConfigurer.class.getClassLoader());

	private final static boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext",
			AnnotatedControllerConfigurer.class.getClassLoader());

	private final static boolean beanValidationPresent = ClassUtils.isPresent(
			"jakarta.validation.executable.ExecutableValidator",
			AnnotatedControllerConfigurer.class.getClassLoader());


	private final FormattingConversionService conversionService = new DefaultFormattingConversionService();

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
	 * Add {@link HandlerMethodArgumentResolver}'s for custom controller method
	 * arguments. Such custom resolvers are ordered after built-in resolvers
	 * except for {@link SourceMethodArgumentResolver}, which is always last.
	 *
	 * @param resolvers the resolvers to add.
	 * @since 1.2
	 */
	public void setCustomArgumentResolver(List<HandlerMethodArgumentResolver> resolvers) {
		this.customArgumentResolvers.addAll(resolvers);
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
	 * @since 1.2
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
		GraphQlArgumentBinder argumentBinder = new GraphQlArgumentBinder(this.conversionService);
		resolvers.addResolver(new ArgumentMethodArgumentResolver(argumentBinder));
		resolvers.addResolver(new ArgumentsMethodArgumentResolver(argumentBinder));
		resolvers.addResolver(new ContextValueMethodArgumentResolver());
		resolvers.addResolver(new LocalContextValueMethodArgumentResolver());

		// Type based
		resolvers.addResolver(new DataFetchingEnvironmentMethodArgumentResolver());
		resolvers.addResolver(new DataLoaderMethodArgumentResolver());
		if (springSecurityPresent) {
			resolvers.addResolver(new PrincipalMethodArgumentResolver());
			BeanResolver beanResolver = new BeanFactoryResolver(obtainApplicationContext());
			resolvers.addResolver(new AuthenticationPrincipalArgumentResolver(beanResolver));
		}
		if (KotlinDetector.isKotlinPresent()) {
			resolvers.addResolver(new ContinuationHandlerMethodArgumentResolver());
		}

		this.customArgumentResolvers.forEach(resolvers::addResolver);

		// This works as a fallback, after all other resolvers
		resolvers.addResolver(new SourceMethodArgumentResolver());

		return resolvers;
	}

	protected final ApplicationContext obtainApplicationContext() {
		Assert.state(this.applicationContext != null, "No ApplicationContext");
		return this.applicationContext;
	}


	@Override
	public void configure(RuntimeWiring.Builder runtimeWiringBuilder) {
		Assert.state(this.argumentResolvers != null, "`argumentResolvers` is not initialized");
		Assert.state(this.exceptionResolver != null, "`exceptionResolver` is not initialized");

		findHandlerMethods().forEach((info) -> {
			DataFetcher<?> dataFetcher;
			if (!info.isBatchMapping()) {
				dataFetcher = new SchemaMappingDataFetcher(
						info, this.argumentResolvers, this.validationHelper, this.exceptionResolver, this.executor);
			}
			else {
				String dataLoaderKey = registerBatchLoader(info);
				dataFetcher = new BatchMappingDataFetcher(dataLoaderKey);
			}
			runtimeWiringBuilder.type(info.getCoordinates().getTypeName(), typeBuilder ->
					typeBuilder.dataFetcher(info.getCoordinates().getFieldName(), dataFetcher));
		});
	}
	public static FieldCoordinates getCoordinates(DataFetchingEnvironment environment) {
		final String fieldName = environment.getFieldDefinition().getName();
		final GraphQLType parent = environment.getParentType();
		if (!(parent instanceof GraphQLObjectType)) {
			return null;
		}
		final String typeName = ((GraphQLObjectType) parent).getName();
		return FieldCoordinates.coordinates(typeName, fieldName);
	}

	public SchemaMappingDataFetcher createDataFetcher(DataFetchingEnvironment environment) {
		final FieldCoordinates coordinates = getCoordinates(environment);
		final MappingInfo info = findDtoMethod(coordinates, environment.getSource(), environment.getSource().getClass());
		if (info == null) {
			return null;
		}
		return new SchemaMappingDataFetcher(info, this.argumentResolvers, validationHelper, exceptionResolver, executor);
	}

	private MappingInfo findDtoMethod(FieldCoordinates coordinates, Object handler, Class<?> handlerClass) {
		if (handlerClass == null) {
			return null;
		}

		Class<?> userClass = ClassUtils.getUserClass(handlerClass);
		Map<Method, MappingInfo> map = MethodIntrospector.selectMethods(
			userClass, (Method method) -> getMappingInfoForDtoField(coordinates, method, handler, userClass));

		Collection<MappingInfo> mappingInfos = map.values();

		if (logger.isTraceEnabled() && !mappingInfos.isEmpty()) {
			logger.trace(formatMappings(userClass, mappingInfos));
		}

		final List<MappingInfo> infos = mappingInfos.stream().filter(mappingInfo -> mappingInfo.getCoordinates().equals(coordinates)).toList();
		if (infos.isEmpty()) {
			return null;
		} else if (infos.size() > 1) {
			throw new IllegalStateException("Ambiguous mapping. Cannot map '" + handler + "' method \n" +
				infos + "\nto " + coordinates + ": There is already '" +
				handler + "' bean method\n" + infos + " mapped.");
		} else {
			return infos.get(0);
		}
	}

	@Nullable
	private MappingInfo getMappingInfoForDtoField(FieldCoordinates coordinates, Method method, Object handler, Class<?> handlerType) {

		Set<Annotation> annotations = AnnotatedElementUtils.findAllMergedAnnotations(
			method, new LinkedHashSet<>(Arrays.asList(SchemaMapping.class)));

		if (annotations.isEmpty()) {
			return null;
		}

		final SchemaMapping mapping = (SchemaMapping) Iterables.getOnlyElement(annotations);

		final HandlerMethod handlerMethod = createHandlerMethod(method, handler, handlerType);
		final String field = (StringUtils.hasText(mapping.field()) ? mapping.field() : method.getName());

		if (!field.equals(coordinates.getFieldName())) {
			return null;
		}

		return new MappingInfo(coordinates.getTypeName(), coordinates.getFieldName(), false, -1, handlerMethod);
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
			findHandlerMethods(beanName, beanClass).forEach((info) -> {
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
		return (handler instanceof String ?
				new HandlerMethod((String) handler, obtainApplicationContext().getAutowireCapableBeanFactory(), theMethod) :
				new HandlerMethod(handler, theMethod));
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

	private String registerBatchLoader(MappingInfo info) {
		if (!info.isBatchMapping()) {
			throw new IllegalArgumentException("Not a @BatchMapping method: " + info);
		}

		String dataLoaderKey = info.getCoordinates().toString();
		BatchLoaderRegistry registry = obtainApplicationContext().getBean(BatchLoaderRegistry.class);

		HandlerMethod handlerMethod = info.getHandlerMethod();
		BatchLoaderHandlerMethod invocable = new BatchLoaderHandlerMethod(handlerMethod, this.executor);

		MethodParameter returnType = handlerMethod.getReturnType();
		Class<?> clazz = returnType.getParameterType();
		Class<?> nestedClass = (clazz.equals(Callable.class) ? returnType.nested().getNestedParameterType() : clazz);

		BatchLoaderRegistry.RegistrationSpec<Object, Object> registration = registry.forName(dataLoaderKey);
		if (info.getMaxBatchSize() > 0) {
			registration.withOptions(options -> options.setMaxBatchSize(info.getMaxBatchSize()));
		}

		if (clazz.equals(Flux.class) || Collection.class.isAssignableFrom(nestedClass)) {
			registration.registerBatchLoader(invocable::invokeForIterable);
		}
		else if (clazz.equals(Mono.class) || nestedClass.equals(Map.class)) {
			registration.registerMappedBatchLoader(invocable::invokeForMap);
		}
		else {
			throw new IllegalStateException("@BatchMapping method is expected to return " +
					"Flux<V>, List<V>, Mono<Map<K, V>>, or Map<K, V>: " + handlerMethod);
		}

		return dataLoaderKey;
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
	public static class SchemaMappingDataFetcher implements TypedDataFetcher<Object> {

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
					(helper != null ? helper.getValidationHelperFor(info.getHandlerMethod()) : null);

			// Register controllers early to validate exception handler return types
			Class<?> controllerType = info.getHandlerMethod().getBeanType();
			exceptionResolver.registerController(controllerType);

			this.exceptionResolver = exceptionResolver;

			this.executor = executor;
			this.subscription = this.info.getCoordinates().getTypeName().equalsIgnoreCase("Subscription");
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

		private Mono<DataFetcherResult<?>> handleException(
				Throwable ex, DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod) {

			return this.exceptionResolver.resolveException(ex, env, handlerMethod.getBean())
					.map(errors -> DataFetcherResult.newResult().errors(errors).build());
		}

		private <T> Publisher<T> handleSubscriptionError(
				Throwable ex, DataFetchingEnvironment env, DataFetcherHandlerMethod handlerMethod) {

			return this.exceptionResolver.resolveException(ex, env, handlerMethod.getBean())
					.flatMap(errors -> Mono.error(new SubscriptionPublisherException(errors, ex)));
		}

		@Override
		public ResolvableType getDeclaredType() {
			return ResolvableType.forMethodReturnType(this.info.getHandlerMethod().getMethod());
		}

	}


	static class BatchMappingDataFetcher implements DataFetcher<Object> {

		private final String dataLoaderKey;

		BatchMappingDataFetcher(String dataLoaderKey) {
			this.dataLoaderKey = dataLoaderKey;
		}

		@Override
		public Object get(DataFetchingEnvironment env) {
			DataLoader<?, ?> dataLoader = env.getDataLoaderRegistry().getDataLoader(this.dataLoaderKey);
			if (dataLoader == null) {
				throw new IllegalStateException("No DataLoader for key '" + this.dataLoaderKey + "'");
			}
			return dataLoader.load(env.getSource());
		}
	}

}
