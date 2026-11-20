/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;

/**
 * Strategy to convert an Object that represents the position of an item within
 * a paginated result set to and from a String cursor.
 *
 * <p>A {@link CursorEncoder} may be combined with a {@link CursorEncoder} via
 * {@link #withEncoder(CursorStrategy, CursorEncoder)} to further encode and
 * decode cursor Strings to make them opaque for clients.
 *
 * @param <P> the type of position
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public interface CursorStrategy<P> {

	/**
	 * Whether the strategy supports the given type of position Object.
	 * @param targetType the type of position to be checked
	 */
	boolean supports(Class<?> targetType);

	/**
	 * Format the given position Object as a String cursor.
	 * @param position the position to serialize
	 * @return the created String cursor
	 */
	String toCursor(P position);

	/**
	 * Parse the given String cursor into a position Object.
	 * @param cursor the cursor to parse
	 * @return the position Object
	 */
	P fromCursor(String cursor);


	/**
	 * Decorate the given {@code CursorStrategy} with encoding and decoding
	 * that makes the String cursor opaque to clients.
	 * @param <T> the type of position for the given strategy
	 * @param strategy the cursor strategy to decorate
	 * @param encoder strategy for encoding the cursor
	 */
	static <T> EncodingCursorStrategy<T> withEncoder(CursorStrategy<T> strategy, CursorEncoder encoder) {
		return new EncodingCursorStrategy<>(strategy, encoder);
	}
}
