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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.context.ApplicationContext;
import org.springframework.core.ExceptionDepthComparator;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.method.ControllerAdviceBean;

/**
 * Resolves exceptions via {@link GraphQlExceptionHandler @GraphQlExceptionHandler}
 * handler methods, which can be either local to a controller, or applicable
 * across controllers and {@link graphql.schema.DataFetcher}s when declared in
 * an {@link org.springframework.web.bind.annotation.ControllerAdvice} bean.
 *
 * <p>This {@link #resolveException(Throwable, DataFetchingEnvironment, Object)}
 * method is similar to the {@link DataFetcherExceptionResolver} contract except
 * it takes an additional, optional argument with the controller that raised the
 * exception, for finding exception handler methods relative to the controller.
 *
 * <p>{@code AnnotatedControllerExceptionResolver} is package private and
 * automatically applied from {@link AnnotatedControllerConfigurer} to controller
 * method invocations. In addition, you can access it as a
 * {@link DataFetcherExceptionResolver} via
 * {@link AnnotatedControllerConfigurer#getExceptionResolver()} to extend
 * exception handling with {@code @ControllerAdvice} exception handlers to
 * non-controller {@link graphql.schema.DataFetcher}s.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
final class AnnotatedControllerExceptionResolver {

	private static final Log logger = LogFactory.getLog(AnnotatedControllerExceptionResolver.class);


	private final HandlerMethodArgumentResolverComposite argumentResolvers;

	private final Map<Class<?>, MethodResolver> controllerCache = new ConcurrentHashMap<>(64);

	private final Map<ControllerAdviceBean, MethodResolver> controllerAdviceCache = new ConcurrentHashMap<>(64);


	AnnotatedControllerExceptionResolver(HandlerMethodArgumentResolverComposite resolvers) {
		Assert.notNull(resolvers, "'resolvers' are required");
		this.argumentResolvers = resolvers;
	}


	/**
	 * Detect {@link GraphQlExceptionHandler} methods in the given controller
	 * class, and save this information for use at runtime. Method return types
	 * are validated to ensure they are within a range of supported types.
	 * @param controllerType the controller type to register
	 */
	public void registerController(Class<?> controllerType) {
		this.controllerCache.computeIfAbsent(
				controllerType, type -> new MethodResolver(findExceptionHandlers(controllerType)));
	}

	/**
	 * Find {@link org.springframework.web.bind.annotation.ControllerAdvice}
	 * beans in the given {@code ApplicationContext}, and detect
	 * {@link GraphQlExceptionHandler} methods in them, saving this information
	 * for use at runtime.
	 * @param context the context to look into
	 */
	public void registerControllerAdvice(ApplicationContext context) {
		for (ControllerAdviceBean bean : ControllerAdviceBean.findAnnotatedBeans(context)) {
			Class<?> beanType = bean.getBeanType();
			if (beanType != null) {
				Map<Class<? extends Throwable>, Method> methods = findExceptionHandlers(beanType);
				if (!methods.isEmpty()) {
					this.controllerAdviceCache.put(bean, new MethodResolver(methods));
				}
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("@GraphQlException methods in ControllerAdvice beans: " +
					(this.controllerAdviceCache.size() == 0 ? "none" : this.controllerAdviceCache.size()));
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<Class<? extends Throwable>, Method> findExceptionHandlers(Class<?> handlerType) {

		Map<Method, GraphQlExceptionHandler> handlerMap = MethodIntrospector.selectMethods(
				handlerType, (MethodIntrospector.MetadataLookup<GraphQlExceptionHandler>) method ->
						AnnotatedElementUtils.findMergedAnnotation(method, GraphQlExceptionHandler.class));

		Map<Class<? extends Throwable>, Method> mappings = new HashMap<>(handlerMap.size());
		handlerMap.forEach((method, annotation) -> {
			List<Class<? extends Throwable>> exceptionTypes = new ArrayList<>();
			if (!ObjectUtils.isEmpty(annotation.value())) {
				exceptionTypes.addAll(Arrays.asList(annotation.value()));
			}
			else {
				for (Class<?> parameterType : method.getParameterTypes()) {
					if (Throwable.class.isAssignableFrom(parameterType)) {
						exceptionTypes.add((Class<? extends Throwable>) parameterType);
					}
				}
			}
			Assert.state(!exceptionTypes.isEmpty(), () -> "No exception types for " + method);
			for (Class<? extends Throwable> type : exceptionTypes) {
				Method oldMethod = mappings.put(type, method);
				Assert.state(oldMethod == null || oldMethod.equals(method), () ->
						"Ambiguous @GraphQlExceptionHandler for [" + type + "]: {" + oldMethod + ", " + method + "}");
			}
		});
		return mappings;
	}


	/**
	 * Resolve the exception with an {@code @GraphQlExceptionHandler} method.
	 * If a controller is provided, look for a matching exception handler in the
	 * controller first, and then in any applicable {@code @ControllerAdvice}.
	 * If a controller is not provided, look in all {@code @ControllerAdvice}.
	 * @param ex the exception to resolve
	 * @param environment the environment for the invoked {@code DataFetcher}
	 * @param controller the controller that raised the exception, if applicable
	 * @return a {@code Mono} with resolved {@code GraphQLError}s as specified in
	 * {@link DataFetcherExceptionResolver#resolveException(Throwable, DataFetchingEnvironment)}
	 */
	public Mono<List<GraphQLError>> resolveException(
			Throwable ex, DataFetchingEnvironment environment, @Nullable Object controller) {

		Object controllerOrAdvice = null;
		MethodHolder methodHolder = null;
		Class<?> controllerType = null;

		if (controller != null) {
			controllerType = ClassUtils.getUserClass(controller.getClass());
			MethodResolver methodResolver = this.controllerCache.get(controllerType);
			if (methodResolver != null) {
				controllerOrAdvice = controller;
				methodHolder = methodResolver.resolveMethod(ex);
			}
			else if (logger.isWarnEnabled()) {
				logger.warn("No registration for controller type: " + controllerType.getName());
			}
		}

		if (methodHolder == null) {
			for (Map.Entry<ControllerAdviceBean, MethodResolver> entry : this.controllerAdviceCache.entrySet()) {
				ControllerAdviceBean advice = entry.getKey();
				if (controller == null || advice.isApplicableToBeanType(controllerType)) {
					methodHolder = entry.getValue().resolveMethod(ex);
					if (methodHolder != null) {
						controllerOrAdvice = advice.resolveBean();
						break;
					}
				}
			}
		}

		if (methodHolder == null) {
			return Mono.empty();
		}

		return invokeExceptionHandler(ex, environment, controllerOrAdvice, methodHolder);
	}

	private Mono<List<GraphQLError>> invokeExceptionHandler(
			Throwable exception, DataFetchingEnvironment env, Object controllerOrAdvice, MethodHolder methodHolder) {

		DataFetcherHandlerMethod exceptionHandler = new DataFetcherHandlerMethod(
				new HandlerMethod(controllerOrAdvice, methodHolder.getMethod()), this.argumentResolvers,
				null, null, false);

		List<Throwable> exceptions = new ArrayList<>();
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Handling exception with " + exceptionHandler);
			}

			// Expose causes as provided arguments as well
			Throwable exToExpose = exception;
			while (exToExpose != null) {
				exceptions.add(exToExpose);
				Throwable cause = exToExpose.getCause();
				exToExpose = cause != exToExpose ? cause : null;
			}
			Object[] arguments = new Object[exceptions.size() + 1];
			exceptions.toArray(arguments);  // efficient arraycopy call in ArrayList
			arguments[arguments.length - 1] = exceptionHandler;

			Object result = exceptionHandler.invoke(env, arguments);

			return methodHolder.adapt(result, exception);
		}
		catch (Throwable invocationEx) {
			// Any other than the original exception (or a cause) is unintended here,
			// probably an accident (e.g. failed assertion or the like).
			if (!exceptions.contains(invocationEx) && logger.isWarnEnabled()) {
				logger.warn("Failure while handling exception with " + exceptionHandler, invocationEx);
			}
			// Continue with processing of the original exception...
			return Mono.error(exception);
		}
	}


	/**
	 * Helps to resolve Exception instances to handler methods.
	 */
	private static final class MethodResolver {

		@SuppressWarnings("DataFlowIssue")
		private static final MethodHolder NO_MATCH =
				new MethodHolder(ReflectionUtils.findMethod(MethodResolver.class, "noMatch"));


		private final Map<Class<? extends Throwable>, MethodHolder> exceptionMappings = new HashMap<>(16);

		private final Map<Class<? extends Throwable>, MethodHolder> resolvedExceptionCache = new ConcurrentReferenceHashMap<>(16);

		MethodResolver(Map<Class<? extends Throwable>, Method> methodMap) {
			methodMap.forEach((exceptionType, method) ->
					this.exceptionMappings.put(exceptionType, new MethodHolder(method)));
		}

		/**
		 * Find an exception handler method mapped to the given exception, using
		 * {@link ExceptionDepthComparator} if more than one match is found.
		 * @param exception the exception
		 * @return the exception handler to use, or {@code null} if no match
		 */
		@Nullable
		public MethodHolder resolveMethod(Throwable exception) {
			MethodHolder method = resolveMethodByExceptionType(exception.getClass());
			if (method == null) {
				Throwable cause = exception.getCause();
				if (cause != null) {
					method = resolveMethod(cause);
				}
			}
			return method;
		}

		@Nullable
		private MethodHolder resolveMethodByExceptionType(Class<? extends Throwable> exceptionType) {
			MethodHolder method = this.resolvedExceptionCache.get(exceptionType);
			if (method == null) {
				method = getMappedMethod(exceptionType);
				this.resolvedExceptionCache.put(exceptionType, method);
			}
			return method != NO_MATCH ? method : null;
		}

		private MethodHolder getMappedMethod(Class<? extends Throwable> exceptionType) {
			List<Class<? extends Throwable>> matches = new ArrayList<>();
			for (Class<? extends Throwable> mappedException : this.exceptionMappings.keySet()) {
				if (mappedException.isAssignableFrom(exceptionType)) {
					matches.add(mappedException);
				}
			}
			if (!matches.isEmpty()) {
				if (matches.size() > 1) {
					matches.sort(new ExceptionDepthComparator(exceptionType));
				}
				return this.exceptionMappings.get(matches.get(0));
			}
			else {
				return NO_MATCH;
			}
		}

		@SuppressWarnings("unused")
		private void noMatch() {
		}

	}


	/**
	 * Container for an exception handler method, and an adapter for its return values.
	 */
	private static class MethodHolder {

		private final Method method;

		private final MethodParameter returnType;

		private final ReturnValueAdapter adapter;

		MethodHolder(Method method) {
			Assert.notNull(method, "Method is required");
			this.method = method;
			this.returnType = new MethodParameter(method, -1);
			this.adapter = ReturnValueAdapter.createFor(this.returnType);
		}

		public Method getMethod() {
			return this.method;
		}

		public Mono<List<GraphQLError>> adapt(@Nullable Object result, Throwable ex) {
			return this.adapter.adapt(result, this.returnType, ex);
		}

	}


	/**
	 * Contract to adapt the value returned from a {@code @GraphQlExceptionHandler}.
	 */
	@SuppressWarnings("unchecked")
	private interface ReturnValueAdapter {

		/**
		 * Adapt the given return value to {@code Mono<List<GraphQLError>>}.
		 * @param result the return value
		 * @param returnType the return type of the method, mainly used for error logging
		 * @param ex the exception being handled
		 * @return the adapted result according to the contact for
		 * {@link DataFetcherExceptionResolver#resolveException(Throwable, DataFetchingEnvironment)}
		 */
		Mono<List<GraphQLError>> adapt(@Nullable Object result, MethodParameter returnType, Throwable ex);

		/**
		 * Verify the method return type is supported and can be adapted to
		 * {@code Mono<List<GraphQLError>>}, and create a suitable adapter.
		 * @param returnType the return type of the method
		 * @return the chosen adapter
		 * @throws IllegalStateException if the return value type that cannot be
		 * adapted to {@code Mono<List<GraphQLError>>} and is not supported
		 */
		static ReturnValueAdapter createFor(MethodParameter returnType) {
			Class<?> parameterType = returnType.getParameterType();
			Method method = returnType.getMethod();
			if (method != null && KotlinDetector.isSuspendingFunction(method)) {
				return createForMono(returnType);
			}
			else if (parameterType == void.class) {
				return forVoid;
			}
			else if (parameterType.equals(GraphQLError.class)) {
				return forSingleError;
			}
			else if (Collection.class.isAssignableFrom(parameterType)) {
				if (returnType.nested().getNestedParameterType().equals(GraphQLError.class)) {
					return forCollection;
				}
			}
			else if (Mono.class.isAssignableFrom(parameterType)) {
				return createForMono(returnType.nested());
			}
			else if (parameterType.equals(Object.class)) {
				return forObject;
			}
			throw new IllegalStateException(
					"Invalid return type for @GraphQlExceptionHandler method: " + returnType);
		}

		private static ReturnValueAdapter createForMono(MethodParameter returnType) {
			Class<?> nestedType = returnType.getNestedParameterType();
			if (nestedType == Void.class) {
				return forMonoVoid;
			}
			if (Collection.class.isAssignableFrom(nestedType)) {
				returnType = returnType.nested();
				nestedType = returnType.getNestedParameterType();
			}
			if (nestedType.equals(GraphQLError.class) || nestedType.equals(Object.class)) {
				return forMono;
			}
			throw new IllegalStateException(
					"Invalid return type for @GraphQlExceptionHandler method: " + returnType);
		}


		/** Adapter for void */
		ReturnValueAdapter forVoid = (result, returnType, ex) -> Mono.just(Collections.emptyList());

		/** Adapter for a single GraphQLError */
		ReturnValueAdapter forSingleError = (result, returnType, ex) ->
				(result == null ?
						Mono.empty() :
						Mono.just(Collections.singletonList((GraphQLError) result)));

		/** Adapter for a collection of GraphQLError's */
		ReturnValueAdapter forCollection = (result, returnType, ex) ->
				(result == null ?
						Mono.empty() :
						Mono.just((result instanceof List ?
								(List<GraphQLError>) result :
								new ArrayList<>((Collection<GraphQLError>) result))));

		/** Adapter for Object */
		ReturnValueAdapter forObject = (result, returnType, ex) -> {
			if (result == null) {
				return Mono.empty();
			}
			else if (result instanceof GraphQLError) {
				return forSingleError.adapt(result, returnType, ex);
			}
			else if (result instanceof Collection<?>) {
				return forCollection.adapt(result, returnType, ex);
			}
			else {
				if (logger.isWarnEnabled()) {
					logger.warn("Unexpected return value of type " +
							result.getClass().getName() + " from method " + returnType);
				}
				return Mono.error(ex);
			}
		};

		/** Adapter for {@code Mono<Void>} */
		ReturnValueAdapter forMonoVoid = (result, returnType, ex) ->
				(result == null ? Mono.empty() : Mono.just(Collections.emptyList()));

		/** Adapter for a {@code Mono} wrapping any of the other synchronous return value types */
		ReturnValueAdapter forMono = (result, returnType, ex) ->
				(result == null ?
						Mono.empty() :
						((Mono<?>) result).flatMap(o -> forObject.adapt(o, returnType, ex)).switchIfEmpty(Mono.error(ex)));
	}

}
