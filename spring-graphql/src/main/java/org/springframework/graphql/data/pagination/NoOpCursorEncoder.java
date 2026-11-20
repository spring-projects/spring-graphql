/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;

/**
 * {@link CursorEncoder} that leaves the cursor value unchanged.
 *
 * <p>To create an instance, use {@link CursorEncoder#noOpEncoder()}.
 *
 * @author Rossen Stoyanchev
 */
final class NoOpCursorEncoder implements CursorEncoder {

	@Override
	public String encode(String cursor) {
		return cursor;
	}

	@Override
	public String decode(String cursor) {
		return cursor;
	}

}
