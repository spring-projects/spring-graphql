/*
 * Copyright 2002-2021 the original author or authors.
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

import java.util.Arrays;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
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

	private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	public DataFetcherHandlerMethod(HandlerMethod handlerMethod, HandlerMethodArgumentResolverComposite resolvers) {
		super(handlerMethod);
		Assert.isTrue(!resolvers.getResolvers().isEmpty(), "No argument resolvers");
		this.resolvers = resolvers;
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
	 * <p>Argument values are commonly resolved through
	 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 * The {@code providedArgs} parameter however may supply argument values to
	 * be used directly, i.e. without argument resolution. Provided argument
	 * values are checked before argument resolvers.
	 * @param environment the GraphQL {@link DataFetchingEnvironment}
	 * @return the raw value returned by the invoked method
	 * @throws Exception raised if no suitable argument resolver can be found,
	 * or if the method raised an exception
	 * @see #getMethodArgumentValues
	 * @see #doInvoke
	 */
	@Nullable
	public Object invoke(DataFetchingEnvironment environment) throws Exception {
		Object[] args = getMethodArgumentValues(environment);
		if (logger.isTraceEnabled()) {
			logger.trace("Arguments: " + Arrays.toString(args));
		}
		return doInvoke(args);
	}

	/**
	 * Get the method argument values for the current request, checking the provided
	 * argument values and falling back to the configured argument resolvers.
	 * <p>The resulting array will be passed into {@link #doInvoke}.
	 */
	protected Object[] getMethodArgumentValues(
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

}
