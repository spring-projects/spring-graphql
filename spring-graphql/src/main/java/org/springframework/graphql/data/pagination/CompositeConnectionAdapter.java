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

package org.springframework.graphql.data.pagination;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * {@link ConnectionAdapter} that contains a list of others adapter, looks for
 * the first one that supports a given Object container type, and delegates to it.
 *
 * @author Rossen Stoyanchev
 */
final class CompositeConnectionAdapter implements ConnectionAdapter {

	private final List<ConnectionAdapter> adapters;


	CompositeConnectionAdapter(List<ConnectionAdapter> adapters) {
		Assert.notEmpty(adapters, "ConnectionAdapter's are required");
		this.adapters = adapters;
	}


	@Override
	public boolean supports(Class<?> containerType) {
		return (getAdapter(containerType) != null);
	}

	@Override
	public <T> Collection<T> getContent(Object container) {
		return getRequiredAdapter(container).getContent(container);
	}

	@Override
	public boolean hasPrevious(Object container) {
		return getRequiredAdapter(container).hasPrevious(container);
	}

	@Override
	public boolean hasNext(Object container) {
		return getRequiredAdapter(container).hasNext(container);
	}

	@Override
	public String cursorAt(Object container, int index) {
		return getRequiredAdapter(container).cursorAt(container, index);
	}

	private ConnectionAdapter getRequiredAdapter(Object container) {
		ConnectionAdapter adapter = getAdapter(container.getClass());
		Assert.notNull(adapter, "No ConnectionAdapter for: " + container.getClass().getName());
		return adapter;
	}

	private @Nullable ConnectionAdapter getAdapter(Class<?> containerType) {
		for (ConnectionAdapter adapter : this.adapters) {
			if (adapter.supports(containerType)) {
				return adapter;
			}
		}
		return null;
	}

}
