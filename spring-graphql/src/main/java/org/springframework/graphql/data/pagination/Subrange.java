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
import java.util.OptionalInt;

import org.springframework.lang.Nullable;

/**
 * Container for parameters that limit result elements to a subrange including a
 * relative position, number of elements, and direction.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class Subrange<P> {

	private final Optional<P> position;

	private final OptionalInt count;

	private final boolean forward;


	/**
	 * Constructor with the relative position, count, and direction.
	 */
	public Subrange(@Nullable P position, @Nullable Integer count, boolean forward) {
		this.position = Optional.ofNullable(position);
		this.count = count != null ? OptionalInt.of(count) : OptionalInt.empty();
		this.forward = forward;
	}


	/**
	 * The position of the result element the subrange is relative to. This is
	 * decoded from the "before" or "after" input arguments from the GraphQL
	 * Cursor connection spec via {@link CursorStrategy}.
	 */
	public Optional<P> position() {
		return this.position;
	}

	/**
	 * The number of elements in the subrange based on the "first" and "last"
	 * arguments from the GraphQL Cursor connection spec.
	 */
	public OptionalInt count() {
		return this.count;
	}

	/**
	 * Whether the subrange is forward or backward from ths position, depending
	 * on whether the argument sent "fist" or "last".
	 * <p><strong>Note:</strong> The direction may not always match the original
	 * value. For backward pagination, for example, an offset cursor could be
	 * adjusted down by the count of elements, switching backward to forward.
	 */
	public boolean forward() {
		return this.forward;
	}

}
