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
 * Strategy to encode and decode a String cursor to make it opaque for clients.
 * Typically applied to a {@link CursorStrategy} via
 * {@link CursorStrategy#withEncoder(CursorStrategy, CursorEncoder)}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public interface CursorEncoder {

	/**
	 * Encode the given cursor value for external use.
	 * @param cursor the cursor to encode
	 * @return the encoded value
	 */
	String encode(String cursor);

	/**
	 * Decode the given cursor from external input.
	 * @param cursor the raw cursor to decode
	 * @return the decoded value
	 */
	String decode(String cursor);


	/**
	 * Return a {@code CursorEncoder} for Base64 encoding and decoding.
	 */
	static CursorEncoder base64() {
		return new Base64CursorEncoder();
	}

	/**
	 * Return a {@code CursorEncoder} that does not encode nor decode.
	 */
	static CursorEncoder noOpEncoder() {
		return new NoOpCursorEncoder();
	}

}
