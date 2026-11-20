/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;


import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

/**
 * Container for parameters that limit result elements to a subrange including a
 * relative position, number of elements, and direction.
 *
 * @param <P> the type of position in the entire collection
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
	 * @param position the position in the entire collection
	 * @param count the number of elements in the subrange
	 * @param forward whether the subrange is forward or backward from ths position
	 */
	public Subrange(@Nullable P position, @Nullable Integer count, boolean forward) {
		this.position = Optional.ofNullable(position);
		this.count = (count != null) ? OptionalInt.of(count) : OptionalInt.empty();
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
	 * on whether the argument sent "first" or "last".
	 * <p><strong>Note:</strong> The direction may not always match the original
	 * value. For backward pagination, for example, an offset cursor could be
	 * adjusted down by the count of elements, switching backward to forward.
	 */
	public boolean forward() {
		return this.forward;
	}

}
