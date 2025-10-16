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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import graphql.schema.DataFetcher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Convenient base for classes that find annotated controller method with argument
 * values resolved from a {@link graphql.schema.DataFetchingEnvironment}.
 *
 * @param <M> the type of mapping info prepared from a controller method
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public abstract class AnnotatedControllerDetectionSupport<M> implements ApplicationContextAware, InitializingBean {

	protected static final boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext",
			AnnotatedControllerDetectionSupport.class.getClassLoader());

	private static final boolean virtualThreadsPresent =
			(ReflectionUtils.findMethod(Thread.class, "ofVirtual") != null);

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


	protected final Log logger = LogFactory.getLog(getClass());


	private final FormattingConversionService conversionService = new DefaultFormattingConversionService();

	private boolean fallBackOnDirectFieldAccess;

	@Nullable
	private AnnotatedControllerExceptionResolver exceptionResolver;

	@Nullable
	private Executor executor;

	private Predicate<HandlerMethod> blockingMethodPredicate =
			(virtualThreadsPresent) ? new BlockingHandlerMethodPredicate() : ((method) -> false);

	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	private Predicate<Class<?>> controllerPredicate =
			(beanType) -> AnnotatedElementUtils.hasAnnotation(beanType, Controller.class);

	@Nullable
	private ApplicationContext applicationContext;


	/**
	 * Add a {@code FormatterRegistrar} to customize the {@link ConversionService}
	 * that assists in binding GraphQL arguments onto
	 * {@link org.springframework.graphql.data.method.annotation.Argument @Argument}
	 * annotated method parameters.
	 * @param registrar the formatter registrar
	 */
	public void addFormatterRegistrar(FormatterRegistrar registrar) {
		registrar.registerFormatters(this.conversionService);
	}

	protected FormattingConversionService getConversionService() {
		return this.conversionService;
	}

	/**
	 * Whether binding GraphQL arguments onto
	 * {@link org.springframework.graphql.data.method.annotation.Argument @Argument}
	 * should falls back to direct field access in case the target object does
	 * not use accessor methods.
	 * @param fallBackOnDirectFieldAccess whether binding should fall back on direct field access
	 * @since 1.2.0
	 */
	public void setFallBackOnDirectFieldAccess(boolean fallBackOnDirectFieldAccess) {
		this.fallBackOnDirectFieldAccess = fallBackOnDirectFieldAccess;
	}

	protected boolean isFallBackOnDirectFieldAccess() {
		return this.fallBackOnDirectFieldAccess;
	}

	/**
	 * Return a {@link DataFetcherExceptionResolver} that resolves exceptions with
	 * {@code @GraphQlExceptionHandler} methods in {@code @ControllerAdvice}
	 * classes declared in Spring configuration. This is useful primarily for
	 * exceptions from non-controller {@link DataFetcher}s since exceptions from
	 * {@code @SchemaMapping} controller methods are handled automatically at
	 * the point of invocation.
	 * @return a resolver instance that can be plugged into
	 * {@link org.springframework.graphql.execution.GraphQlSource.Builder#exceptionResolvers(List)
	 * GraphQlSource.Builder}
	 * @since 1.2.0
	 */
	public HandlerDataFetcherExceptionResolver getExceptionResolver() {
		Assert.notNull(this.exceptionResolver, "afterPropertiesSet not called yet");
		return this.exceptionResolver;
	}

	/**
	 * Configure an {@link Executor} to use for asynchronous handling of
	 * {@link Callable} return values from controller methods, as well as for
	 * {@link #setBlockingMethodPredicate(Predicate) blocking controller methods}
	 * on Java 21+.
	 * <p>By default, this is not set in which case controller methods with a
	 * {@code Callable} return value are not supported, and blocking methods
	 * will be invoked synchronously.
	 * @param executor the executor to use
	 */
	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	/**
	 * Return the {@link #setExecutor(Executor) configured Executor}.
	 */
	@Nullable
	public Executor getExecutor() {
		return this.executor;
	}

	/**
	 * Configure a predicate to decide which controller methods are blocking.
	 * On Java 21+, such methods are invoked asynchronously through the
	 * {@link #setExecutor(Executor) configured Executor}, unless the executor
	 * is a thread pool executor as determined via
	 * {@link SchedulingTaskExecutor#prefersShortLivedTasks() prefersShortLivedTasks}.
	 * <p>By default, on Java 21+ the predicate returns false for controller
	 * method return types known to {@link ReactiveAdapterRegistry} as well as
	 * {@link KotlinDetector#isSuspendingFunction Kotlin suspending functions}.
	 * On Java 20 and lower, the predicate returns false. You can configure the
	 * predicate for more control, or alternatively, return {@link Callable}.
	 * @param predicate the predicate to use
	 * @since 1.3
	 */
	public void setBlockingMethodPredicate(@Nullable Predicate<HandlerMethod> predicate) {
		this.blockingMethodPredicate = ((predicate != null) ? predicate : (handlerMethod) -> false);
	}

	/**
	 * Return the configured argument resolvers.
	 */
	protected HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		Assert.notNull(this.argumentResolvers, "afterPropertiesSet not called yet");
		return this.argumentResolvers;
	}

	/**
	 * Configure a predicate to determine if a given class should be introspected
	 * for annotated controller methods.
	 * <p>The default predicate looks for a type-level {@link Controller} annotation.
	 * @param controllerPredicate the predicate to use
	 * @since 1.4.3
	 */
	public void setControllerPredicate(Predicate<Class<?>> controllerPredicate) {
		this.controllerPredicate = controllerPredicate;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Nullable
	protected ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	protected final ApplicationContext obtainApplicationContext() {
		Assert.state(this.applicationContext != null, "No ApplicationContext");
		return this.applicationContext;
	}


	@Override
	public void afterPropertiesSet() {
		this.argumentResolvers = initArgumentResolvers();

		this.exceptionResolver = new AnnotatedControllerExceptionResolver(this.argumentResolvers);
		if (getApplicationContext() != null) {
			this.exceptionResolver.registerControllerAdvice(getApplicationContext());
		}
	}

	protected abstract HandlerMethodArgumentResolverComposite initArgumentResolvers();


	/**
	 * Scan beans in the ApplicationContext, detect and prepare a map of handler methods.
	 */
	protected Set<M> detectHandlerMethods() {
		Set<M> results = new LinkedHashSet<>();
		ApplicationContext context = obtainApplicationContext();
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
				if (this.logger.isTraceEnabled()) {
					this.logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
				}
			}
			if (beanType == null || !this.controllerPredicate.test(beanType)) {
				continue;
			}
			Class<?> beanClass = context.getType(beanName);
			findHandlerMethods(beanName, beanClass).forEach((info) -> addHandlerMethod(info, results));
		}

		return results;
	}

	private Collection<M> findHandlerMethods(Object handler, @Nullable Class<?> handlerClass) {
		if (handlerClass == null) {
			return Collections.emptyList();
		}

		Class<?> userClass = ClassUtils.getUserClass(handlerClass);
		Map<Method, M> map = MethodIntrospector.selectMethods(
				userClass, (Method method) -> getMappingInfo(method, handler, userClass));

		return map.values();
	}

	@Nullable
	protected abstract M getMappingInfo(Method method, Object handler, Class<?> handlerType);

	protected HandlerMethod createHandlerMethod(Method originalMethod, Object handler, Class<?> handlerType) {
		Method method = AopUtils.selectInvocableMethod(originalMethod, handlerType);
		return (handler instanceof String beanName) ?
				new HandlerMethod(beanName, obtainApplicationContext().getAutowireCapableBeanFactory(), method) :
				new HandlerMethod(handler, method);
	}

	private void addHandlerMethod(M info, Set<M> results) {
		Assert.state(this.exceptionResolver != null, "afterPropertiesSet not called");
		HandlerMethod handlerMethod = getHandlerMethod(info);
		M existing = results.stream().filter((o) -> o.equals(info)).findFirst().orElse(null);
		if (existing != null && !getHandlerMethod(existing).equals(handlerMethod)) {
			throw new IllegalStateException(
					"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
							handlerMethod + "\n" + ": There is already '" +
							getHandlerMethod(existing).getBean() + "' bean method\n" + existing + " mapped.");
		}
		results.add(info);
		this.exceptionResolver.registerController(handlerMethod.getBeanType());
	}

	protected abstract HandlerMethod getHandlerMethod(M mappingInfo);

	protected boolean shouldInvokeAsync(HandlerMethod handlerMethod) {
		return (this.blockingMethodPredicate.test(handlerMethod) && this.executor != null &&
				!(this.executor instanceof SchedulingTaskExecutor ste && ste.prefersShortLivedTasks()));
	}


	private static final class BlockingHandlerMethodPredicate implements Predicate<HandlerMethod> {

		@Override
		public boolean test(HandlerMethod hm) {
			Class<?> returnType = hm.getReturnType().getParameterType();
			return (ReactiveAdapterRegistry.getSharedInstance().getAdapter(returnType) == null &&
					!KotlinDetector.isSuspendingFunction(hm.getMethod()));
		}
	}

}
