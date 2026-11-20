/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
 * @author Oliver Drotbohm
 * @since 1.2.0
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
	 * @param keysetCursorStrategy the keyset cursor strategy
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
					return ScrollPosition.offset((index > 0) ? index : 0);
				}
				else if (cursor.startsWith(KEYSET_PREFIX)) {
					Map<String, Object> keys = this.keysetCursorStrategy.fromCursor(cursor.substring(2));
					return ScrollPosition.forward(keys);
				}
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Failed to parse cursor: " + cursor, ex);
			}
		}
		throw new IllegalArgumentException("Invalid or unknown cursor type: " + cursor);
	}

}
