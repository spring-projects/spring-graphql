/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.LocalContextValue;
import org.springframework.util.Assert;


/**
 * Resolver for a {@link LocalContextValue @LocalContextValue} annotated method
 * parameter.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class LocalContextValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.getParameterAnnotation(LocalContextValue.class) != null);
	}

	@Override
	public @Nullable Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {

		LocalContextValue annotation = parameter.getParameterAnnotation(LocalContextValue.class);
		Assert.state(annotation != null, "Expected @LocalContextValue annotation");
		String name = ContextValueMethodArgumentResolver.getContextValueName(parameter, annotation.name(), annotation);

		Object localContext = environment.getLocalContext();
		Assert.state(localContext == null || localContext instanceof GraphQLContext,
				"Local context is not an instance of  graphql.GraphQLContext");

		return ContextValueMethodArgumentResolver.resolveContextValue(
				name, annotation.required(), parameter, (GraphQLContext) localContext);
	}


}
