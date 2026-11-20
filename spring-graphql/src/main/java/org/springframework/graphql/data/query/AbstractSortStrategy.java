/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query;


import java.util.ArrayList;
import java.util.List;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Sort;
import org.springframework.util.ObjectUtils;

/**
 * Convenient base class for a {@link SortStrategy}. Subclasses help to extract
 * the list of sort {@link #getProperties(DataFetchingEnvironment) properties}
 * and {@link #getDirection(DataFetchingEnvironment) direction}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public abstract class AbstractSortStrategy implements SortStrategy {

	@Override
	public Sort extract(DataFetchingEnvironment environment) {
		List<String> properties = getProperties(environment);
		if (!ObjectUtils.isEmpty(properties)) {
			Sort.Direction direction = getDirection(environment);
			direction = (direction != null) ? direction : Sort.DEFAULT_DIRECTION;
			List<Sort.Order> sortOrders = new ArrayList<>(properties.size());
			for (String property : properties) {
				sortOrders.add(new Sort.Order(direction, property));
			}
			return Sort.by(sortOrders);
		}
		return Sort.unsorted();
	}

	/**
	 * Return the sort properties to use, or an empty list if there are none.
	 * @param environment the data fetching environment for this operation
	 */
	protected abstract List<String> getProperties(DataFetchingEnvironment environment);

	/**
	 * Return the sort direction to use, or {@code null}.
	 * @param environment the data fetching environment for this operation
	 */
	protected abstract Sort.@Nullable Direction getDirection(DataFetchingEnvironment environment);

}
