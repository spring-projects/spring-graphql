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

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import graphql.schema.DataFetchingEnvironment;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Invocable handler to use as a {@link graphql.schema.DataFetcher}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DataFetcherHandlerMethod extends DataFetcherHandlerMethodSupport {

	private final BiConsumer<Object, Object[]> validationHelper;

	private final boolean subscription;


	/**
	 * Constructor with a parent handler method.
	 * @param handlerMethod the handler method
	 * @param resolvers the argument resolvers
	 * @param validationHelper to apply bean validation with
	 * @param executor an {@link Executor} to use for {@link Callable} return values
	 * @param subscription whether the field being fetched is of subscription type
	 * @deprecated in favor of alternative constructor
	 */
	@Deprecated(since = "1.3.0", forRemoval = true)
	public DataFetcherHandlerMethod(
			HandlerMethod handlerMethod, HandlerMethodArgumentResolverComposite resolvers,
			@Nullable BiConsumer<Object, Object[]> validationHelper, @Nullable Executor executor,
			boolean subscription) {

		this(handlerMethod, resolvers, validationHelper, executor, subscription, false);
	}

	/**
	 * Constructor with a parent handler method.
	 * @param handlerMethod the handler method
	 * @param resolvers the argument resolvers
	 * @param validationHelper to apply bean validation with
	 * @param executor an {@link Executor} to use for {@link Callable} return values
	 * @param subscription whether the field being fetched is of subscription type
	 * @param invokeAsync whether to invoke the method through the Executor
	 * @since 1.3.0
	 */
	public DataFetcherHandlerMethod(
			HandlerMethod handlerMethod, HandlerMethodArgumentResolverComposite resolvers,
			@Nullable BiConsumer<Object, Object[]> validationHelper,
			@Nullable Executor executor, boolean invokeAsync, boolean subscription) {

		super(handlerMethod, resolvers, executor, invokeAsync);
		Assert.isTrue(!resolvers.getResolvers().isEmpty(), "No argument resolvers");
		this.validationHelper = (validationHelper != null) ? validationHelper : (controller, args) -> { };
		this.subscription = subscription;
	}


	/**
	 * Invoke the method after resolving its argument values in the context of
	 * the given {@link DataFetchingEnvironment}.
	 *
	 * <p>Argument values are commonly resolved through
	 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 * The {@code providedArgs} parameter however may supply argument values to
	 * be used directly, i.e. without argument resolution. Provided argument
	 * values are checked before argument resolvers.
	 * @param environment the environment to resolve arguments from
	 * @return the raw value returned by the invoked method, possibly a
	 * {@code Mono} in case a method argument requires asynchronous resolution;
	 * {@code Mono<Throwable>} is returned if invocation fails.
	 */
	@Nullable
	public Object invoke(DataFetchingEnvironment environment) {
		return invoke(environment, new Object[0]);
	}

	/**
	 * Variant of {@link #invoke(DataFetchingEnvironment)} that also accepts
	 * "given" arguments, which are matched by type.
	 * @param environment the data fetching environment
	 * @param providedArgs additional arguments to be matched by their type
	 * @since 1.2.0
	 */
	@Nullable
	public Object invoke(DataFetchingEnvironment environment, Object... providedArgs) {
		Object[] args;
		try {
			args = getMethodArgumentValues(environment, providedArgs);
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}

		if (Arrays.stream(args).noneMatch((arg) -> arg instanceof Mono)) {
			return validateAndInvoke(args, environment);
		}

		return this.subscription ?
				toArgsMono(args).flatMapMany((argValues) -> {
					Object result = validateAndInvoke(argValues, environment);
					Assert.state(result instanceof Publisher, "Expected a Publisher from a Subscription response");
					return Flux.from((Publisher<?>) result);
				}) :
				toArgsMono(args).flatMap((argValues) -> {
					Object result = validateAndInvoke(argValues, environment);
					if (result instanceof Mono<?> mono) {
						return mono;
					}
					else if (result instanceof Flux<?> flux) {
						return Flux.from(flux).collectList();
					}
					else if (result instanceof CompletableFuture<?> future) {
						return Mono.fromFuture(future);
					}
					else {
						return Mono.justOrEmpty(result);
					}
				});
	}

	@Nullable
	private Object validateAndInvoke(Object[] args, DataFetchingEnvironment environment) {
		this.validationHelper.accept(getBean(), args);
		return doInvoke(environment.getGraphQlContext(), args);
	}

}
