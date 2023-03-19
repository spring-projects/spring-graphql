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
import org.springframework.graphql.data.pagination.Subrange;
import org.springframework.lang.Nullable;

/**
 * Container for parameters that limit result elements to a subrange including a
 * relative {@link ScrollPosition}, number of elements, and direction.
 *
 * <p> For backward pagination, the offset of an {@link OffsetScrollPosition}
 * is adjusted to point to the first item in the range by subtracting the count
 * from it. Hence, for {@code OffsetScrollPosition} {@link #forward()} is
 * always {@code true}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public final class ScrollSubrange extends Subrange<ScrollPosition> {


	public ScrollSubrange(@Nullable ScrollPosition pos, @Nullable Integer count, boolean forward) {
		super(initPosition(pos, count, forward), count, (pos instanceof OffsetScrollPosition || forward));
	}

	@Nullable
	private static ScrollPosition initPosition(@Nullable ScrollPosition pos, @Nullable Integer count, boolean forward) {
		if (!forward) {
			if (pos instanceof OffsetScrollPosition offsetPosition && count != null) {
				long offset = offsetPosition.getOffset();
				return OffsetScrollPosition.of(offset > count ? offset - count : 0);
			}
			else if (pos instanceof KeysetScrollPosition keysetPosition) {
				Map<String, Object> keys = keysetPosition.getKeys();
				pos = KeysetScrollPosition.of(keys, Direction.Backward);
			}
		}
		return pos;
	}

}
