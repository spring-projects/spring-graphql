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


import java.util.ArrayList;
import java.util.List;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
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
			direction = direction != null ? direction : Sort.DEFAULT_DIRECTION;
			List<Sort.Order> sortOrders = new ArrayList<>(properties.size());
			for (String property : properties) {
				sortOrders.add(new Sort.Order(direction, property));
			}
			return Sort.by(sortOrders);
		}
		return null;
	}

	/**
	 * Return the sort properties to use, or an empty list if there are none.
	 */
	protected abstract List<String> getProperties(DataFetchingEnvironment environment);

	/**
	 * Return the sort direction to use, or {@code null}.
	 */
	@Nullable
	protected abstract Sort.Direction getDirection(DataFetchingEnvironment environment);

}
