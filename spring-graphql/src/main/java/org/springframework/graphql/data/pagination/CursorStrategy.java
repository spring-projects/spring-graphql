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

package org.springframework.graphql.data.pagination;

/**
 * Strategy to convert an Object that represents the position of an item within
 * a paginated result set to and from a String cursor.
 *
 * <p>A {@link CursorEncoder} may be combined with a {@link CursorEncoder} via
 * {@link #withEncoder(CursorStrategy, CursorEncoder)} to further encode and
 * decode cursor Strings to make them opaque for clients.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public interface CursorStrategy<P> {

	/**
	 * Whether the strategy supports the given type of position Object.
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
	 */
	static <T> EncodingCursorStrategy<T> withEncoder(CursorStrategy<T> strategy, CursorEncoder encoder) {
		return new EncodingCursorStrategy<>(strategy, encoder);
	}
}
