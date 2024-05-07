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

import java.util.concurrent.Executor;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.InvocableHandlerMethodSupport;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;


/**
 * Extension of {@link InvocableHandlerMethodSupport} for handler methods that
 * resolve argument values from a {@link DataFetchingEnvironment}.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public class DataFetcherHandlerMethodSupport extends InvocableHandlerMethodSupport {

	private static final Object[] EMPTY_ARGS = new Object[0];

	protected final HandlerMethodArgumentResolverComposite resolvers;

	private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	protected DataFetcherHandlerMethodSupport(
			HandlerMethod handlerMethod, HandlerMethodArgumentResolverComposite resolvers,
			@Nullable Executor executor, boolean invokeAsync) {

		super(handlerMethod, executor, invokeAsync);
		this.resolvers = resolvers;
	}


	/**
	 * Return the configured argument resolvers.
	 */
	public HandlerMethodArgumentResolverComposite getResolvers() {
		return this.resolvers;
	}


	/**
	 * Get the method argument values for the current request, checking the provided
	 * argument values and falling back to the configured argument resolvers.
	 * @param environment the data fetching environment to resolve arguments from
	 * @param providedArgs the arguments provided directly
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
