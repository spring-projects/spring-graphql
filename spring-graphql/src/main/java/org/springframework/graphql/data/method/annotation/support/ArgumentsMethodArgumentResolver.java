/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Arguments;
import org.springframework.util.Assert;

/**
 * Resolver for a method parameter that is annotated with
 * {@link Arguments @Arguments}, similar to what
 * {@link ArgumentMethodArgumentResolver} does but using the full
 * full {@link DataFetchingEnvironment#getArgument(String) GraphQL arguments}
 * map as the source for binding to the target Object rather than a specific
 * argument value within it.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see org.springframework.graphql.data.method.annotation.Arguments
 * @see org.springframework.graphql.data.method.annotation.Argument
 * @see org.springframework.graphql.data.GraphQlArgumentBinder
 */
public class ArgumentsMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final GraphQlArgumentBinder argumentBinder;


	public ArgumentsMethodArgumentResolver(GraphQlArgumentBinder argumentBinder) {
		Assert.notNull(argumentBinder, "GraphQlArgumentBinder is required");
		this.argumentBinder = argumentBinder;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterAnnotation(Arguments.class) != null;
	}

	@Override
	public @Nullable Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
		return this.argumentBinder.bind(environment, null, resolvableType);
	}

}
