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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import graphql.schema.DataFetchingEnvironment;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.InvocableHandlerMethodSupport;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * An extension of {@link HandlerMethod} for annotated handler methods adapted
 * to {@link graphql.schema.DataFetcher} with {@link DataFetchingEnvironment}
 * as their input.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DataFetcherHandlerMethod extends InvocableHandlerMethodSupport {

	private static final Object[] EMPTY_ARGS = new Object[0];


	private final HandlerMethodArgumentResolverComposite resolvers;

	private final BiConsumer<Object, Object[]> validationHelper;
	
	private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	private final boolean subscription;


	/**
	 * Constructor with a parent handler method.
	 * @param handlerMethod the handler method
	 * @param resolvers the argument resolvers
	 * @param validationHelper to apply bean validation with
	 * @param subscription whether the field being fetched is of subscription type
	 */
	public DataFetcherHandlerMethod(
			HandlerMethod handlerMethod, HandlerMethodArgumentResolverComposite resolvers,
			@Nullable BiConsumer<Object, Object[]> validationHelper, @Nullable Executor executor,
			boolean subscription) {

		super(handlerMethod, executor);
		Assert.isTrue(!resolvers.getResolvers().isEmpty(), "No argument resolvers");
		this.resolvers = resolvers;
		this.validationHelper = validationHelper != null ? validationHelper : (controller, args) -> {};
		this.subscription = subscription;
	}


	/**
	 * Return the configured argument resolvers.
	 */
	public HandlerMethodArgumentResolverComposite getResolvers() {
		return this.resolvers;
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
	 *
	 * @param environment the environment to resolve arguments from
	 *
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

		if (Arrays.stream(args).noneMatch(Mono.class::isInstance)) {
			return validateAndInvoke(args, environment);
		}

		return this.subscription ?
				toArgsMono(args).flatMapMany(argValues -> {
					Object result = validateAndInvoke(argValues, environment);
					Assert.state(result instanceof Publisher, "Expected a Publisher from a Subscription response");
					return Flux.from((Publisher<?>) result);
				}) :
				toArgsMono(args).flatMap(argValues -> {
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

	/**
	 * Get the method argument values for the current request, checking the provided
	 * argument values and falling back to the configured argument resolvers.
	 * <p>The resulting array will be passed into {@link #doInvoke}.
	 */
	private Object[] getMethodArgumentValues(
			DataFetchingEnvironment environment, Object... providedArgs) throws Exception {

		MethodParameter[] parameters = getMethodParameters();
		if (ObjectUtils.isEmpty(parameters)) {
			return EMPTY_ARGS;
		}

		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
			args[i] = findProvidedArgument(parameter, providedArgs);
			if (args[i] != null) {
				continue;
			}
			if (!this.resolvers.supportsParameter(parameter)) {
				throw new IllegalStateException(formatArgumentError(parameter, "No suitable resolver"));
			}
			try {
				args[i] = this.resolvers.resolveArgument(parameter, environment);
			}
			catch (Exception ex) {
				// Leave stack trace for later, exception may actually be resolved and handled...
				if (logger.isDebugEnabled()) {
					String exMsg = ex.getMessage();
					if (exMsg != null && !exMsg.contains(parameter.getExecutable().toGenericString())) {
						logger.debug(formatArgumentError(parameter, exMsg));
					}
				}
				throw ex;
			}
		}
		return args;
	}

	@Nullable
	private Object validateAndInvoke(Object[] args, DataFetchingEnvironment environment) {
		this.validationHelper.accept(getBean(), args);
		return doInvoke(environment.getGraphQlContext(), args);
	}

}
