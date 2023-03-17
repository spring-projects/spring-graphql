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
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.util.Assert;

/**
 * Strategy to convert a {@link ScrollPosition} to and from a String cursor.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public final class ScrollPositionCursorStrategy implements CursorStrategy<ScrollPosition> {

	private static final String OFFSET_PREFIX = "O_";

	private static final String KEYSET_PREFIX = "K_";


	private final CursorStrategy<Map<String, Object>> keysetCursorStrategy;


	/**
	 * Shortcut constructor that uses {@link JsonKeysetCursorStrategy}.
	 */
	public ScrollPositionCursorStrategy() {
		this(new JsonKeysetCursorStrategy());
	}

	/**
	 * Constructor with a given strategy to convert a
	 * {@link KeysetScrollPosition#getKeys() keyset} to and from a cursor.
	 */
	public ScrollPositionCursorStrategy(CursorStrategy<Map<String, Object>> keysetCursorStrategy) {
		Assert.notNull(keysetCursorStrategy, "'keysetCursorStrategy' is required");
		this.keysetCursorStrategy = keysetCursorStrategy;
	}


	@Override
	public boolean supports(Class<?> targetType) {
		return ScrollPosition.class.isAssignableFrom(targetType);
	}

	@Override
	public String toCursor(ScrollPosition position) {
		if (position instanceof OffsetScrollPosition offsetPosition) {
			return OFFSET_PREFIX + offsetPosition.getOffset();
		}
		else if (position instanceof KeysetScrollPosition keysetPosition) {
			return KEYSET_PREFIX + this.keysetCursorStrategy.toCursor(keysetPosition.getKeys());
		}
		throw new IllegalArgumentException("Unexpected ScrollPosition type: " + position.getClass().getName());
	}

	@Override
	public ScrollPosition fromCursor(String cursor) {
		if (cursor.length() > 2) {
			try {
				if (cursor.startsWith(OFFSET_PREFIX)) {
					long index = Long.parseLong(cursor.substring(2));
					return OffsetScrollPosition.of(index > 0 ? index : 0);
				}
				else if (cursor.startsWith(KEYSET_PREFIX)) {
					Map<String, Object> keys = this.keysetCursorStrategy.fromCursor(cursor.substring(2));
					return KeysetScrollPosition.of(keys);
				}
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Failed to parse cursor: " + cursor, ex);
			}
		}
		throw new IllegalArgumentException("Invalid or unknown cursor type: " + cursor);
	}

}
