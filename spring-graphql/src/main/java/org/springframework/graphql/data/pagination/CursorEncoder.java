/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;

/**
 * Strategy to encode and decode a String cursor to make it opaque for clients.
 * Typically applied to a {@link CursorStrategy} via
 * {@link CursorStrategy#withEncoder(CursorStrategy, CursorEncoder)}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
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
