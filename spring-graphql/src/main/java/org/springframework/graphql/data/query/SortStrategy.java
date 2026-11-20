/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query;


import graphql.schema.DataFetchingEnvironment;

import org.springframework.data.domain.Sort;

/**
 * Strategy to extract {@link Sort} details from GraphQL arguments.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public interface SortStrategy {

	/**
	 * Return a {@link Sort} instance by extracting the sort information from
	 * GraphQL arguments, or {@link Sort#unsorted()} otherwise.
	 * @param environment the data fetching environment
	 */
	Sort extract(DataFetchingEnvironment environment);

}
