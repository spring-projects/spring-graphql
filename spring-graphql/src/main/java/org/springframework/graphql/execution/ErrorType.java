/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import graphql.ErrorClassification;

/**
 * Common categories to use to classify for exceptions raised by
 * {@link graphql.schema.DataFetcher}'s that can enable a client to make automated
 * decisions.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see graphql.GraphqlErrorBuilder#errorType(ErrorClassification)
 */
public enum ErrorType implements ErrorClassification {

	/**
	 * {@link graphql.schema.DataFetcher} cannot or will not fetch the data value due to
	 * something that is perceived to be a client error.
	 */
	BAD_REQUEST,

	/**
	 * {@link graphql.schema.DataFetcher} did not fetch the data value due to a lack of
	 * valid authentication credentials.
	 */
	UNAUTHORIZED,

	/**
	 * {@link graphql.schema.DataFetcher} refuses to authorize the fetching of the data
	 * value.
	 */
	FORBIDDEN,

	/**
	 * {@link graphql.schema.DataFetcher} did not find a data value or is not willing to
	 * disclose that one exists.
	 */
	NOT_FOUND,

	/**
	 * {@link graphql.schema.DataFetcher} encountered an unexpected condition that
	 * prevented it from fetching the data value.
	 */
	INTERNAL_ERROR

}
