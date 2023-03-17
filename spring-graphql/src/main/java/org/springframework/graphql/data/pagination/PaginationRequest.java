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


import java.util.Optional;

import org.springframework.lang.Nullable;

/**
 * Container for a pagination request.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public class PaginationRequest<P> {

	@Nullable
	private final P position;

	@Nullable
	private final Integer count;

	private final boolean forward;


	/**
	 * Constructor with the position, count, and direction.
	 */
	public PaginationRequest(@Nullable P position, @Nullable Integer count, boolean forward) {
		this.position = position;
		this.forward = forward;
		this.count = count;
	}


	/**
	 * The position of an element relative to which to paginate, decoded from a
	 * String cursor, e.g. the "before" and "after" arguments from the GraphQL
	 * Cursor connection spec.
	 */
	public Optional<P> position() {
		return Optional.ofNullable(this.position);
	}

	/**
	 * The number of elements requested, e.g. "first" and "last" N elements
	 * arguments from the GraphQL Cursor connection spec.
	 */
	public Optional<Integer> count() {
		return Optional.ofNullable(this.count);
	}

	/**
	 * Whether forward or backward pagination is requested, e.g. depending on
	 * whether "fist" or "last" N elements was sent.
	 * <p><strong>Note:</strong> This value may not reflect the one originally
	 * sent by the client. For example, for backward pagination, an offset cursor
	 * may be adjusted down by the number of requested elements, turning into
	 * forward pagination.
	 */
	public boolean forward() {
		return this.forward;
	}

}
