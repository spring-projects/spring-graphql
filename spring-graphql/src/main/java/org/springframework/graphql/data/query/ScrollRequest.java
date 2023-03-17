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

package org.springframework.graphql.data.query;


import java.util.Map;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.KeysetScrollPosition.Direction;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.PaginationRequest;
import org.springframework.lang.Nullable;

/**
 * Container for pagination request with a {@link ScrollPosition} cursor.
 *
 * <p>An {@link OffsetScrollPosition} is always used for forward pagination.
 * When backward pagination is requested, the offset is adjusted down by the
 * requested count, thus turning it into forward pagination.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public final class ScrollRequest extends PaginationRequest<ScrollPosition> {


	public ScrollRequest(@Nullable ScrollPosition position, @Nullable Integer count, boolean forward) {
		super(initPosition(position, count, forward), count,
				(position instanceof OffsetScrollPosition || forward));
	}

	@Nullable
	private static ScrollPosition initPosition(
			@Nullable ScrollPosition position, @Nullable Integer count, boolean forward) {

		if (!forward) {
			if (position instanceof OffsetScrollPosition offsetPosition && count != null) {
				long offset = offsetPosition.getOffset();
				return OffsetScrollPosition.of(offset > count ? offset - count : 0);
			}
			else if (position instanceof KeysetScrollPosition keysetPosition) {
				Map<String, Object> keys = keysetPosition.getKeys();
				position = KeysetScrollPosition.of(keys, Direction.Backward);
			}
		}

		return position;
	}

}
