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


import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * Represents a GraphQL response with the result of executing a request operation.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlResponse {

	/**
	 * Whether the response is valid. A response is invalid in one of the
	 * following two cases:
	 * <ul>
	 * <li>the {@link #toMap() response map} has no "data" entry indicating
	 * errors before execution, e.g. grammar parse and validation
	 * <li>the "data" entry has a {@code null} value indicating errors during
	 * execution that prevented a valid response
	 * </ul>
	 * <p>A valid response has a "data" key with a {@code non-null} value, but
	 * it may still be partial and have some fields set to {@code null} due to
	 * field errors.
	 * <p>For more details, see section 7 "Response" in the GraphQL spec.
	 */
	boolean isValid();

	/**
	 * Return the data part of the response, or {@code null} when the response
	 * is not {@link #isValid() valid}.
	 * @param <T> a map or a list
	 */
	@Nullable
	<T> T getData();

	/**
	 * Return errors included in the response.
	 * <p>A response that is not {@link #isValid() valid} contains "request
	 * errors". Those are errors that apply to the request as a whole, and have
	 * an empty error {@link ResponseError#getPath() path}.
	 * <p>A response that is valid may still be partial and contain "field
	 * errors". Those are errors associated with a specific field through their
	 * error path.
	 */
	List<ResponseError> getErrors();

	/**
	 * Navigate to the given path under the "data" key of the response map where
	 * the path is a dot-separated string with optional array indexes.
	 * <p>Example paths:
	 * <pre>
	 * "hero"
	 * "hero.name"
	 * "hero.friends"
	 * "hero.friends[2]"
	 * "hero.friends[2].name"
	 * </pre>
	 * @param path relative to the "data" key
	 * @return representation for the field with further options to inspect or
	 * decode its value; use {@link ResponseField#getValue()} to check if
	 * the field actually has a value.
	 */
	ResponseField field(String path);

	/**
	 * Return implementor specific, protocol extensions, if any.
	 */
	Map<Object, Object> getExtensions();

	/**
	 * Return a map representation of the response, formatted as required in the
	 * "Response" section of the GraphQL spec.
	 */
	Map<String, Object> toMap();

}
