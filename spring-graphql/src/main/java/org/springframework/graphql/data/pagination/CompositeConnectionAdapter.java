/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * {@link ConnectionAdapter} that wraps a list of other adapters, and delegates
 * to the one that supports a given type of Object container.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public class CompositeConnectionAdapter implements ConnectionAdapter {

	private final List<ConnectionAdapter> adapters;


	public CompositeConnectionAdapter(List<ConnectionAdapter> adapters) {
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
