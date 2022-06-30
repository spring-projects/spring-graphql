/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.graphql.client;

import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;


/**
 * Contract for executing GraphQL requests over some transport.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlTransport {

	/**
	 * Execute a request with a single response such as a "query" or "mutation".
	 * @param request the request to execute
	 * @return a {@code Mono} with the {@code GraphQlResponse} for the response.
	 * The {@code Mono} may end wth an error due to transport or other issues
	 * such as failures to encode the request or decode the response.
	 */
	Mono<GraphQlResponse> execute(GraphQlRequest request);

	/**
	 * Execute a "subscription" request with a stream of responses.
	 * @param request the request to execute
	 * @return a {@code Flux} of {@code GraphQlResponse} responses.
	 * The {@code Flux} may terminate as follows:
	 * <ul>
	 * <li>Completes if the subscription completes before the connection is closed.
	 * <li>{@link SubscriptionErrorException} if the subscription ends with an error.
	 * <li>{@link WebSocketDisconnectedException} if the connection is closed or
	 * lost before the stream terminates.
	 * <li>Exception for connection and GraphQL session initialization issues.
	 * </ul>
	 * <p>The {@code Flux} may be cancelled to notify the server to end the
	 * subscription stream.
	 */
	Flux<GraphQlResponse> executeSubscription(GraphQlRequest request);

    Mono<GraphQlResponse> executeFileUpload(GraphQlRequest request);

	/**
	 * Factory method to create {@link GraphQlResponse} from a GraphQL response
	 * map for use in transport implementations.
	 */
	static GraphQlResponse createResponse(Map<String, Object> responseMap) {
		return new ResponseMapGraphQlResponse(responseMap);
	}

}
