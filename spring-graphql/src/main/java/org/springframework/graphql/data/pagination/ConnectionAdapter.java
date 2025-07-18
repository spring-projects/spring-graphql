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

/**
 * Contract to adapt a container object for a window of elements from a larger
 * result set to {@link graphql.relay.Connection}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public interface ConnectionAdapter {

	/**
	 * Whether the adapter supports the given Object container type.
	 * @param containerType the container type to check for support
	 */
	boolean supports(Class<?> containerType);

	/**
	 * Return the contained items as a List.
	 * @param <T> the type of objects in the collection
	 * @param container the container of elements
	 */
	<T> Collection<T> getContent(Object container);

	/**
	 * Whether there are more pages before this one.
	 * @param container the container of elements
	 */
	boolean hasPrevious(Object container);

	/**
	 * Whether there are more pages after this one.
	 * @param container the container of elements
	 */
	boolean hasNext(Object container);

	/**
	 * Return a cursor for the item at the given index.
	 * @param container the container of elements
	 * @param index the index of an element in the container
	 */
	String cursorAt(Object container, int index);


	/**
	 * Create a composite {@link ConnectionAdapter} that checks which adapter
	 * supports a given Object container type and delegates to it.
	 * @param adapters the adapters to delegate to
	 * @return the composite adapter instance
	 */
	static ConnectionAdapter from(List<ConnectionAdapter> adapters) {
		return new CompositeConnectionAdapter(adapters);
	}

}
