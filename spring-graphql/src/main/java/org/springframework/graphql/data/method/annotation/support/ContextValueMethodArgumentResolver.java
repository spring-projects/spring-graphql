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
package org.springframework.graphql.data.method.annotation.support;

import java.util.Optional;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Resolver for {@link ContextValue @ContextValue} annotated method parameters.
 * Values are resolved through one of the following:
 * <ul>
 * <li>{@link DataFetchingEnvironment#getLocalContext()} -- if it is an
 * instance of {@link GraphQLContext}.
 * <li>{@link DataFetchingEnvironment#getGraphQlContext()}
 * </ul>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ContextValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.getParameterAnnotation(ContextValue.class) != null);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {
		return resolveContextValue(parameter, environment.getLocalContext(), environment.getGraphQlContext());
	}

	@Nullable
	static Object resolveContextValue(
			MethodParameter parameter, @Nullable Object localContext, GraphQLContext graphQlContext) {

		ContextValue annotation = parameter.getParameterAnnotation(ContextValue.class);
		Assert.state(annotation != null, "Expected @ContextValue annotation");
		String name = getValueName(parameter, annotation);

		Class<?> parameterType = parameter.getParameterType();
		Object value = null;

		if (localContext instanceof GraphQLContext) {
			value = ((GraphQLContext) localContext).get(name);
		}

		if (value == null) {
			value = graphQlContext.get(name);
		}

		boolean isOptional = parameterType.equals(Optional.class);
		boolean isMono = parameterType.equals(Mono.class);

		if (value == null && annotation.required() && !isOptional && !isMono) {
			throw new IllegalStateException("Missing required context value for " + parameter);
		}

		if (isMono) {
			if (value == null) {
				value = Mono.empty();
			}
			else if (!( value instanceof Mono)) {
				value = Mono.just(value);
			}
			return Mono.just(value);
		}

		if (isOptional) {
			return (value instanceof Optional ? value : Optional.ofNullable(value));
		}

		return value;
	}

	private static String getValueName(MethodParameter parameter, ContextValue annotation) {
		if (StringUtils.hasText(annotation.name())) {
			return annotation.name();
		}
		String parameterName = parameter.getParameterName();
		if (parameterName != null) {
			return parameterName;
		}
		throw new IllegalArgumentException("Name for @ContextValue argument " +
				"of type [" + parameter.getNestedParameterType().getName() + "] not specified, " +
				"and parameter name information not found in class file either.");
	}

	@Nullable
	private static Object wrapAsOptionalIfNecessary(@Nullable Object value, Class<?> type) {
		return (type.equals(Optional.class) ? Optional.ofNullable(value) : value);
	}

}
