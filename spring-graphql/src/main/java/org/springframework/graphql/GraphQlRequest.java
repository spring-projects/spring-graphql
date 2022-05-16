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

package org.springframework.graphql;

import java.util.Map;

import org.springframework.lang.Nullable;


/**
 * Represents a GraphQL request with the inputs to pass to a GraphQL service
 * including a {@link #getDocument() document}, {@link #getOperationName()
 * operationName}, and {@link #getVariables() variables}.
 *
 * <p>The request can be turned to a Map via {@link #toMap()} and to be
 * submitted as JSON over HTTP or WebSocket.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlRequest {

	/**
	 * Return the GraphQL document which is the textual representation of an
	 * operation (or operations) to perform, including any selection sets and
	 * fragments.
	 */
	String getDocument();

	/**
	 * Return the name of the operation in the {@link #getDocument() document}
	 * to execute, if the document contains multiple operations.
	 */
	@Nullable
	String getOperationName();

	/**
	 * Return values for variable defined by the operation.
	 */
	Map<String, Object> getVariables();

	/**
	 * Return implementor specific, protocol extensions, if any.
	 */
	Map<String, Object> getExtensions();

	/**
	 * Convert the request to a {@link Map} as defined in
	 * <a href="https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md">GraphQL over HTTP</a> and
	 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL over WebSocket</a>:
	 * <table>
	 * <tr><th>Key</th><th>Value</th></tr>
	 * <tr><td>query</td><td>{@link #getDocument() document}</td></tr>
	 * <tr><td>operationName</td><td>{@link #getOperationName() operationName}</td></tr>
	 * <tr><td>variables</td><td>{@link #getVariables() variables}</td></tr>
	 * </table>
	 */
	Map<String, Object> toMap();

}
