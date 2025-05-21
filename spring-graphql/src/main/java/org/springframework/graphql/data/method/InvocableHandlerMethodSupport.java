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

package org.springframework.graphql.data.method;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import graphql.GraphQLContext;
import io.micrometer.context.ContextSnapshot;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.data.util.KotlinReflectionUtils;
import org.springframework.graphql.execution.ContextPropagationHelper;
import org.springframework.util.Assert;

/**
 * Extension of {@link HandlerMethod} that adds support for invoking the
 * underlying handler methods.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public abstract class InvocableHandlerMethodSupport extends HandlerMethod {

	private static final Object NO_VALUE = new Object();


	private final @Nullable Executor executor;

	private final boolean hasCallableReturnValue;

	private final boolean invokeAsync;



	/**
	 * Create an instance.
	 * @param handlerMethod the controller method
	 * @param executor an {@link Executor} to use for {@link Callable} return values
	 * @deprecated in favor of alternative constructor
	 */
	@Deprecated(since = "1.3.0", forRemoval = true)
	protected InvocableHandlerMethodSupport(HandlerMethod handlerMethod, @Nullable Executor executor) {
		this(handlerMethod, executor, false);
	}

	/**
	 * Create an instance.
	 * @param handlerMethod the controller method
	 * @param executor an {@link Executor} to use for {@link Callable} return values
	 * @param invokeAsync whether to invoke the method through the Executor
	 * @since 1.3.0
	 */
	protected InvocableHandlerMethodSupport(
			HandlerMethod handlerMethod, @Nullable Executor executor, boolean invokeAsync) {
		super(handlerMethod.createWithResolvedBean());

		this.executor = executor;
		this.hasCallableReturnValue = getReturnType().getParameterType().equals(Callable.class);
		this.invokeAsync = (invokeAsync && !this.hasCallableReturnValue);

		Assert.isTrue((!this.hasCallableReturnValue && !invokeAsync) || executor != null,
				"Controller method has Callable return value or invokeAsync=true, but Executor not provided: " +
						handlerMethod.getBridgedMethod().toGenericString());
	}


	/**
	 * Invoke the handler method with the given argument values.
	 * @param graphQLContext the GraphQL context for this data fetching operation
	 * @param argValues the values to use to invoke the method
	 * @return the value returned from the method or a {@code Mono<Throwable>}
	 * if the invocation fails.
	 */
	@SuppressWarnings("ReactiveStreamsUnusedPublisher")
	protected @Nullable Object doInvoke(GraphQLContext graphQLContext, @Nullable Object... argValues) {
		if (logger.isTraceEnabled()) {
			logger.trace("Invoking " + getBridgedMethod().getName() + "(" + Arrays.toString(argValues) + ")");
		}
		Method method = getBridgedMethod();
		try {
			if (KotlinDetector.isSuspendingFunction(method)) {
				return invokeSuspendingFunction(getBean(), method, argValues);
			}

			Object result;
			if (this.invokeAsync) {
				Callable<Object> callable = () -> method.invoke(getBean(), argValues);
				result = adaptCallable(graphQLContext, callable, method, argValues);
			}
			else {
				result = method.invoke(getBean(), argValues);
				if (this.hasCallableReturnValue && result != null) {
					result = adaptCallable(graphQLContext, (Callable<?>) result, method, argValues);
				}
			}

			return result;
		}
		catch (IllegalArgumentException ex) {
			return Mono.error(processIllegalArgumentException(argValues, ex, method));
		}
		catch (InvocationTargetException ex) {
			return Mono.error(processInvocationTargetException(argValues, ex));
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}
	}

	@SuppressWarnings({"ReactiveStreamsUnusedPublisher", "unchecked"})
	private static Object invokeSuspendingFunction(Object bean, Method method, @Nullable Object[] argValues) {
		Object result = CoroutinesUtils.invokeSuspendingFunction(method, bean, argValues);

		// Support use of DataLoader from suspending function
		Class<?> returnType = KotlinReflectionUtils.getReturnType(method);
		if (CompletableFuture.class.isAssignableFrom(returnType)) {
			return ((Mono<CompletableFuture<?>>) result).flatMap(Mono::fromFuture);
		}

		return result;
	}

	@SuppressWarnings("DataFlowIssue")
	private CompletableFuture<?> adaptCallable(
			GraphQLContext graphQLContext, Callable<?> result, Method method, @Nullable Object[] argValues) {

		CompletableFuture<Object> future = new CompletableFuture<>();
		Assert.state(this.executor != null, "No Executor configured for Callable return values");
		this.executor.execute(() -> {
			try {
				ContextSnapshot snapshot = ContextPropagationHelper.captureFrom(graphQLContext);
				Object value = snapshot.wrap((Callable<?>) result).call();
				future.complete(value);
			}
			catch (IllegalArgumentException ex) {
				future.completeExceptionally(processIllegalArgumentException(argValues, ex, method));
			}
			catch (InvocationTargetException ex) {
				future.completeExceptionally(processInvocationTargetException(argValues, ex));
			}
			catch (Exception ex) {
				future.completeExceptionally(ex);
			}
		});
		return future;
	}

	private IllegalStateException processIllegalArgumentException(
			@Nullable Object[] argValues, IllegalArgumentException ex, Method method) {

		assertTargetBean(method, getBean(), argValues);
		String text = (ex.getMessage() != null) ? ex.getMessage() : "Illegal argument";
		return new IllegalStateException(formatInvokeError(text, argValues), ex);
	}

	private Throwable processInvocationTargetException(@Nullable Object[] argValues, InvocationTargetException ex) {
		// Unwrap for DataFetcherExceptionResolvers ...
		Throwable targetException = ex.getTargetException();
		if (targetException instanceof Error || targetException instanceof Exception) {
			return targetException;
		}
		String message = formatInvokeError("Invocation failure", argValues);
		return new IllegalStateException(message, targetException);
	}

	/**
	 * Use this method to resolve the arguments asynchronously. This is only
	 * useful when at least one of the values is a {@link Mono}
	 * @param args the arguments to be resolved asynchronously
	 */
	@SuppressWarnings({"unchecked", "NullAway"})
	protected Mono<@Nullable Object[]> toArgsMono(@Nullable Object[] args) {
		List<Mono<Object>> monoList = new ArrayList<>();
		for (Object arg : args) {
			Mono<Object> argMono = ((arg instanceof Mono) ? (Mono<Object>) arg : Mono.justOrEmpty(arg));
			monoList.add(argMono.defaultIfEmpty(NO_VALUE));
		}
		return Mono.zip(monoList, (values) -> {
			for (int i = 0; i < values.length; i++) {
				if (values[i] == NO_VALUE) {
					values[i] = null;
				}
			}
			return values;
		});
	}

}
