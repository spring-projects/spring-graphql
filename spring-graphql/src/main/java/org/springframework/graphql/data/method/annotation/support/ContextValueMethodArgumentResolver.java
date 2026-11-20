/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import java.lang.annotation.Annotation;
import java.util.Optional;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


/**
 * Resolver for a {@link ContextValue @ContextValue} annotated method parameter.
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
	public @Nullable Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {

		ContextValue annotation = parameter.getParameterAnnotation(ContextValue.class);
		Assert.state(annotation != null, "Expected @ContextValue annotation");
		String name = getContextValueName(parameter, annotation.name(), annotation);

		return resolveContextValue(name, annotation.required(), parameter, environment.getGraphQlContext());
	}

	static String getContextValueName(MethodParameter parameter, String nameFromAnnotation, Annotation annotation) {
		if (StringUtils.hasText(nameFromAnnotation)) {
			return nameFromAnnotation;
		}
		String parameterName = parameter.getParameterName();
		if (parameterName != null) {
			return parameterName;
		}
		throw new IllegalArgumentException("Name for " + annotation.getClass().getSimpleName() + " argument " +
				"of type [" + parameter.getNestedParameterType().getName() + "] not specified, " +
				"and parameter name information not found in class file either.");
	}

	static @Nullable Object resolveContextValue(
			String contextValueName, boolean required, MethodParameter parameter,
			@Nullable GraphQLContext graphQlContext) {

		Class<?> parameterType = parameter.getParameterType();
		Object value = (graphQlContext != null) ? graphQlContext.get(contextValueName) : null;

		boolean isOptional = parameterType.equals(Optional.class);
		boolean isMono = parameterType.equals(Mono.class);

		if (value == null && required && !isOptional && !isMono) {
			throw new IllegalStateException("Missing required context value for " + parameter);
		}

		if (isMono) {
			return Mono.just((value instanceof Mono<?> mono) ? mono : Mono.justOrEmpty(value));
		}

		if (isOptional) {
			return (value instanceof Optional) ? value : Optional.ofNullable(value);
		}

		return value;
	}

}
