/*
 * Copyright 2002-2021 the original author or authors.
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
