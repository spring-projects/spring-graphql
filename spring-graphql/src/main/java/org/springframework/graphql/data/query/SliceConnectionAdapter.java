/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query;

import java.util.Collection;

import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.graphql.data.pagination.ConnectionAdapter;
import org.springframework.graphql.data.pagination.ConnectionAdapterSupport;
import org.springframework.graphql.data.pagination.CursorStrategy;

/**
 * Adapter for {@link Slice} to {@link graphql.relay.Connection}.
 *
 * @author Rossen Stoyanchev
 * @author Oliver Drotbohm
 * @since 1.2.0
 */
public final class SliceConnectionAdapter
		extends ConnectionAdapterSupport<ScrollPosition> implements ConnectionAdapter {


	/**
	 * Constructor with the {@link CursorStrategy} to use to encode the
	 * {@code ScrollPosition} of page items.
	 * @param strategy the cursor strategy to use
	 */
	public SliceConnectionAdapter(CursorStrategy<ScrollPosition> strategy) {
		super(strategy);
	}


	@Override
	public boolean supports(Class<?> containerType) {
		return Slice.class.isAssignableFrom(containerType);
	}

	@Override
	public <T> Collection<T> getContent(Object container) {
		Slice<T> slice = slice(container);
		return slice.getContent();
	}

	@Override
	public boolean hasPrevious(Object container) {
		return slice(container).hasPrevious();
	}

	@Override
	public boolean hasNext(Object container) {
		return slice(container).hasNext();
	}

	@Override
	public String cursorAt(Object container, int index) {
		Slice<?> slice = slice(container);
		ScrollPosition position = ScrollPosition.offset((long) slice.getNumber() * slice.getSize() + index);
		return getCursorStrategy().toCursor(position);
	}

	@SuppressWarnings("unchecked")
	private <T> Slice<T> slice(Object container) {
		return (Slice<T>) container;
	}

}
