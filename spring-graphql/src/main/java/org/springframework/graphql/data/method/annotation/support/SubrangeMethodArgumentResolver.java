/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;


import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.pagination.Subrange;
import org.springframework.util.Assert;

/**
 * Resolver for a method argument of type {@link Subrange} initialized
 * from "first", "last", "before", and "after" GraphQL arguments.
 *
 * @param <P> the type of position in the subrange
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public class SubrangeMethodArgumentResolver<P> implements HandlerMethodArgumentResolver {

	private final CursorStrategy<P> cursorStrategy;


	public SubrangeMethodArgumentResolver(CursorStrategy<P> cursorStrategy) {
		Assert.notNull(cursorStrategy, "CursorStrategy is required");
		this.cursorStrategy = cursorStrategy;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.getParameterType().equals(Subrange.class) &&
				this.cursorStrategy.supports(parameter.nested().getNestedParameterType()));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		boolean forward = true;
		String cursor = environment.getArgument("after");
		Integer count = environment.getArgument("first");
		if (cursor == null && count == null) {
			cursor = environment.getArgument("before");
			count = environment.getArgument("last");
			if (cursor != null || count != null) {
				forward = false;
			}
		}
		P pos = (cursor != null) ? this.cursorStrategy.fromCursor(cursor) : null;
		return createSubrange(pos, count, forward);
	}

	/**
	 * Allows subclasses to create an extension of {@link Subrange}.
	 * @param pos the position in the subrange
	 * @param count the number of elements in the subrange
	 * @param forward whether the scroll direction is forward or backward from this position
	 */
	protected Subrange<P> createSubrange(@Nullable P pos, @Nullable Integer count, boolean forward) {
		return new Subrange<>(pos, count, forward);
	}

}
