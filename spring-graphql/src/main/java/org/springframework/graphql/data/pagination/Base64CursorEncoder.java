/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;


import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * {@link CursorEncoder} that applies Base 64 encoding and decoding.
 *
 * <p>To create an instance, use {@link CursorEncoder#base64()}.
 *
 * @author Rossen Stoyanchev
 */
final class Base64CursorEncoder implements CursorEncoder {

	private final Charset charset = StandardCharsets.UTF_8;


	@Override
	public String encode(String cursor) {
		byte[] bytes = Base64.getEncoder().encode(cursor.getBytes(this.charset));
		return new String(bytes, this.charset);
	}

	@Override
	public String decode(String cursor) {
		byte[] bytes = Base64.getDecoder().decode(cursor.getBytes(this.charset));
		return new String(bytes, this.charset);
	}

}
