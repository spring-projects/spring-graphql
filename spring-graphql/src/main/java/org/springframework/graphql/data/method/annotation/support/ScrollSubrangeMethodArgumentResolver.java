/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;


import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.query.ScrollSubrange;


/**
 * A {@link SubrangeMethodArgumentResolver} that supports {@link ScrollSubrange}
 * and {@link ScrollPosition} as cursor.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public class ScrollSubrangeMethodArgumentResolver extends SubrangeMethodArgumentResolver<ScrollPosition> {


	public ScrollSubrangeMethodArgumentResolver(CursorStrategy<ScrollPosition> strategy) {
		super(strategy);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(ScrollSubrange.class);
	}

	@Override
	protected ScrollSubrange createSubrange(@Nullable ScrollPosition pos, @Nullable Integer count, boolean forward) {
		return ScrollSubrange.create(pos, count, forward);
	}

}
