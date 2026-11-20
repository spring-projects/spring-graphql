/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;


/**
 * Implementation of {@link GraphQlResponse} that wraps the {@link ExecutionResult}
 * returned from {@link graphql.GraphQL} to expose it as {@link GraphQlResponse},
 * also providing access to the {@link ExecutionInput} used for the request.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ExecutionGraphQlResponse extends GraphQlResponse {

	/**
	 * Return the {@link ExecutionInput} that was prepared through the
	 * {@link ExecutionGraphQlRequest} and passed to {@link graphql.GraphQL}.
	 */
	ExecutionInput getExecutionInput();

	/**
	 * Return the {@link ExecutionResult} that was returned from the invocation
	 * to {@link graphql.GraphQL}.
	 */
	ExecutionResult getExecutionResult();

}
