/*
 * Copyright 2020-present the original author or authors.
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

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.data.pagination.ConnectionAdapter;
import org.springframework.graphql.data.pagination.ConnectionAdapterSupport;
import org.springframework.graphql.data.pagination.CursorStrategy;

/**
 * Adapter for {@link Window} to {@link graphql.relay.Connection}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public final class WindowConnectionAdapter
		extends ConnectionAdapterSupport<ScrollPosition> implements ConnectionAdapter {


	public WindowConnectionAdapter(CursorStrategy<ScrollPosition> strategy) {
		super(strategy);
	}


	@Override
	public boolean supports(Class<?> containerType) {
		return Window.class.isAssignableFrom(containerType);
	}

	@Override
	public <T> Collection<T> getContent(Object container) {
		Window<T> window = window(container);
		return window.getContent();
	}

	@Override
	public boolean hasPrevious(Object container) {
		Window<?> window = window(container);
		if (!window.isEmpty()) {
			ScrollPosition position = window.positionAt(0);
			if (position instanceof KeysetScrollPosition keysetPosition) {
				return (keysetPosition.scrollsBackward() && window.hasNext());
			}
			else if (position instanceof OffsetScrollPosition offsetPosition) {
				return (offsetPosition.getOffset() != 0);
			}
			else {
				return !position.isInitial();
			}
		}
		return false;
	}

	@Override
	public boolean hasNext(Object container) {
		Window<?> window = window(container);
		if (!window.isEmpty()) {
			ScrollPosition pos = window.positionAt(0);
			if (pos instanceof KeysetScrollPosition keysetPos) {
				return (keysetPos.scrollsForward() && window.hasNext());
			}
			else {
				return window.hasNext();
			}
		}
		return false;
	}

	@Override
	public String cursorAt(Object container, int index) {
		ScrollPosition position = window(container).positionAt(index);
		return getCursorStrategy().toCursor(position);
	}

	@SuppressWarnings("unchecked")
	private <T> Window<T> window(Object container) {
		return (Window<T>) container;
	}

}
