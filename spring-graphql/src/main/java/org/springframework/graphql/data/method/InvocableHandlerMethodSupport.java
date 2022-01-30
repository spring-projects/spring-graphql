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
package org.springframework.graphql.data.method;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import reactor.core.publisher.Mono;

import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.lang.Nullable;

/**
 * Extension of {@link HandlerMethod} that adds support for invoking the
 * underlying handler methods.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public abstract class InvocableHandlerMethodSupport extends HandlerMethod {

	private static final Object NO_VALUE = new Object();


	protected InvocableHandlerMethodSupport(HandlerMethod handlerMethod) {
		super(handlerMethod.createWithResolvedBean());
	}


	/**
	 * Invoke the handler method with the given argument values.
	 * @param argValues the values to use to invoke the method
	 * @return the value returned from the method or a {@code Mono<Throwable>}
	 * if the invocation fails.
	 */
	@Nullable
	protected Object doInvoke(Object... argValues) {
		if (logger.isTraceEnabled()) {
			logger.trace("Arguments: " + Arrays.toString(argValues));
		}
		Method method = getBridgedMethod();
		try {
			if (KotlinDetector.isSuspendingFunction(method)) {
				return CoroutinesUtils.invokeSuspendingFunction(method, getBean(), argValues);
			}
			return method.invoke(getBean(), argValues);
		}
		catch (IllegalArgumentException ex) {
			assertTargetBean(method, getBean(), argValues);
			String text = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
			return Mono.error(new IllegalStateException(formatInvokeError(text, argValues), ex));
		}
		catch (InvocationTargetException ex) {
			// Unwrap for DataFetcherExceptionResolvers ...
			Throwable targetException = ex.getTargetException();
			if (targetException instanceof Error || targetException instanceof Exception) {
				return Mono.error(targetException);
			}
			else {
				return Mono.error(new IllegalStateException(
						formatInvokeError("Invocation failure", argValues), targetException));
			}
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}
	}

	/**
	 * Use this method to resolve the arguments asynchronously. This is only
	 * useful when at least one of the values is a {@link Mono}
	 */
	@SuppressWarnings("unchecked")
	protected Mono<Object[]> toArgsMono(Object[] args) {

		List<Mono<Object>> monoList = Arrays.stream(args)
				.map(arg -> {
					Mono<Object> argMono = (arg instanceof Mono ? (Mono<Object>) arg : Mono.justOrEmpty(arg));
					return argMono.defaultIfEmpty(NO_VALUE);
				})
				.collect(Collectors.toList());

		return Mono.zip(monoList,
				values -> Stream.of(values).map(value -> value != NO_VALUE ? value : null).toArray());
	}

}
