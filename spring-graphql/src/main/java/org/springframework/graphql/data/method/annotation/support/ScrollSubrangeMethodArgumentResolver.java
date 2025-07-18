/*
 * Copyright 2020-present the original author or authors.
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
