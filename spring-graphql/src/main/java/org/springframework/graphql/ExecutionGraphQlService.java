/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import reactor.core.publisher.Mono;

/**
 * Strategy to execute a GraphQL request by invoking GraphQL Java.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ExecutionGraphQlService {

	/**
	 * Execute the request and return the response.
	 * @param request the request to execute
	 * @return the resulting response
	 */
	Mono<ExecutionGraphQlResponse> execute(ExecutionGraphQlRequest request);

}
