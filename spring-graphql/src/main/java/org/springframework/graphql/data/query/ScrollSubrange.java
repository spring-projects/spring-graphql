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

package org.springframework.graphql.data.query;


import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.Subrange;

/**
 * {@link Subrange} implementation for a {@link ScrollPosition} cursor.
 *
 * @author Rossen Stoyanchev
 * @author Oliver Drotbohm
 * @since 1.2.0
 */
public final class ScrollSubrange extends Subrange<ScrollPosition> {


	@SuppressWarnings("unused")
	private ScrollSubrange(@Nullable ScrollPosition pos, @Nullable Integer count, boolean forward) {
		super(pos, count, forward);
	}


	/**
	 * Create a {@link ScrollSubrange} from the given inputs.
	 * <p>Offset scrolling is always forward and exclusive of the referenced item.
	 * Therefore, for backward pagination, the offset is advanced back by the
	 * count + 1, and the direction is switched to forward.
	 * @param position the reference position, or {@code null} if not specified
	 * @param count how many to return, or {@code null} if not specified
	 * @param forward whether scroll forward (true) or backward (false)
	 * @return the created instance
	 * @since 1.2.4
	 */
	public static ScrollSubrange create(
			@Nullable ScrollPosition position, @Nullable Integer count, boolean forward) {

		if (count != null && count < 0) {
			count = null;
		}
		if (position instanceof OffsetScrollPosition offsetScrollPosition) {
			return initFromOffsetPosition(offsetScrollPosition, count, forward);
		}
		else if (position instanceof KeysetScrollPosition keysetScrollPosition) {
			return initFromKeysetPosition(keysetScrollPosition, count, forward);
		}
		else {
			return new ScrollSubrange(position, count, forward);
		}
	}

	private static ScrollSubrange initFromOffsetPosition(
			OffsetScrollPosition position, @Nullable Integer count, boolean forward) {

		// Offset is exclusive:
		//  - for forward, nothing to do
		//  - for backward, subtract (count + 1) to get items before position

		if (!forward) {
			// Advance back by 1 at least to item before position
			int advanceCount = ((count != null) ? count : 1);

			// Add 1 more to exclude item at reference position
			advanceCount++;

			if (position.getOffset() >= advanceCount) {
				position = position.advanceBy(-advanceCount);
			}
			else {
				count = (int) position.getOffset();
				position = ScrollPosition.offset();
			}
		}

		return new ScrollSubrange(position, count, true);
	}

	private static ScrollSubrange initFromKeysetPosition(
			KeysetScrollPosition position, @Nullable Integer count, boolean forward) {

		if (!forward) {
			position = position.backward();
		}
		return new ScrollSubrange(position, count, forward);
	}

}
