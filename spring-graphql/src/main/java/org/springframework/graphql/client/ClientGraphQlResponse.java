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


import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlResponse;

/**
 * {@link GraphQlResponse} for client use with further options to navigate and
 * handle the selection set in the response.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ClientGraphQlResponse extends GraphQlResponse {


	/**
	 * Navigate to the given path under the "data" key of the response map and
	 * return a representation with further options to decode the field value,
	 * or to check whether it's valid, and so on.
	 * @param path relative to the "data" key.
	 * @return a representation for the field at the given path; this
	 */
	ResponseField field(String path);

	/**
	 * Decode the full response map to the given target type.
	 * @param type the target class
	 * @return the decoded value
	 */
	<D> D toEntity(Class<D> type);

	/**
	 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
	 * @param type the target type
	 * @return the decoded value
	 */
	<D> D toEntity(ParameterizedTypeReference<D> type);

}
