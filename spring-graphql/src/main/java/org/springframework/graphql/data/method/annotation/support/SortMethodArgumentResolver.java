/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;


import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.query.SortStrategy;
import org.springframework.util.Assert;


/**
 * Resolver for method arguments of type {@link Sort}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public class SortMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final SortStrategy sortStrategy;


	public SortMethodArgumentResolver(SortStrategy sortStrategy) {
		Assert.notNull(sortStrategy, "SortStrategy is required");
		this.sortStrategy = sortStrategy;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.nestedIfOptional().getNestedParameterType().equals(Sort.class);
	}

	@SuppressWarnings("ConstantValue")
	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {

		Sort sort = this.sortStrategy.extract(environment);

		if (parameter.isOptional()) {
			sort = (sort == Sort.unsorted()) ? null : sort;
			return Optional.ofNullable(sort);
		}

		return (sort != null) ? sort : Sort.unsorted();
	}

}
