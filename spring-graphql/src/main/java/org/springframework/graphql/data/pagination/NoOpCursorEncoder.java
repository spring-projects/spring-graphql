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
 * {@link CursorEncoder} that leaves the cursor value unchanged.
 *
 * <p>To create an instance, use {@link CursorEncoder#noOpEncoder()}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
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
