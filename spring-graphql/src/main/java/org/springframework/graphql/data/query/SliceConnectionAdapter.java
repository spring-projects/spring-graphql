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

package org.springframework.graphql.data.query;

import java.util.Collection;

import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.graphql.data.pagination.ConnectionAdapter;
import org.springframework.graphql.data.pagination.ConnectionAdapterSupport;
import org.springframework.graphql.data.pagination.CursorStrategy;

/**
 * Adapter for {@link Slice} to {@link graphql.relay.Connection}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public final class SliceConnectionAdapter
		extends ConnectionAdapterSupport<ScrollPosition> implements ConnectionAdapter {


	/**
	 * Constructor with the {@link CursorStrategy} to use to encode the
	 * {@code ScrollPosition} of page items.
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
		ScrollPosition position = OffsetScrollPosition.of((long) slice.getNumber() * slice.getSize() + index);
		return getCursorStrategy().toCursor(position);
	}

	@SuppressWarnings("unchecked")
	private <T> Slice<T> slice(Object container) {
		return (Slice<T>) container;
	}

}
