/*
 * Copyright 2020-2023 the original author or authors.
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


import org.springframework.core.MethodParameter;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.query.ScrollRequest;
import org.springframework.lang.Nullable;


/**
 * Subclass of {@link PaginationRequestMethodArgumentResolver} that supports
 * {@link ScrollRequest} with cursors converted to {@link ScrollPosition} for
 * forward or backward pagination.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public class ScrollRequestMethodArgumentResolver extends PaginationRequestMethodArgumentResolver<ScrollPosition> {


	public ScrollRequestMethodArgumentResolver(CursorStrategy<ScrollPosition> cursorStrategy) {
		super(cursorStrategy);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(ScrollRequest.class);
	}

	protected ScrollRequest createRequest(@Nullable ScrollPosition position, @Nullable Integer size, boolean forward) {
		return new ScrollRequest(position, size, forward);
	}

}
