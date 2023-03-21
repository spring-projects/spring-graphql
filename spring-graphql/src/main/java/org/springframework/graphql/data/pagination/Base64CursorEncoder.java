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


import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * {@link CursorEncoder} that applies Base 64 encoding and decoding.
 *
 * <p>To create an instance, use {@link CursorEncoder#base64()}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
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
