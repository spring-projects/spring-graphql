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

import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;


/**
 * Representation for a field in a GraphQL response, with options to examine
 * the field value and errors.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlResponseField {

	/**
	 * Whether the field has a value.
	 * <ul>
	 * <li>{@code "true"} means the field is not {@code null}, and therefore valid,
	 * although it may be partial with nested field {@link #getErrors() errors}.
	 * <li>{@code "false"} means the field is {@code null} or doesn't exist; use
	 * {@link #getError()} to check if the field is {@code null} due to an error.
	 * </ul>
	 */
	boolean hasValue();

	/**
	 * Return a String representation of the field path as described in
	 * {@link ClientGraphQlResponse#field(String)}.
	 */
	String getPath();

	/**
	 * Return a parsed representation of the field path, in the format described
	 * for error paths in Section 7.1.2, "Response Format" of the GraphQL spec.
	 * @see GraphQlResponseError#getParsedPath()
	 */
	List<Object> getParsedPath();

	/**
	 * Return the raw field value, e.g. Map, List, or a scalar type.
	 * @param <T> the expected value type to cast to
	 * @return the value
	 */
	@Nullable
	<T> T getValue();

	/**
	 * Return the error that provides the reason for a failed field.
	 * <p>When the field <strong>does not</strong> {@link #hasValue() have} a
	 * value, this method looks for the first field error. According to the
	 * GraphQL spec, section 6.4.4, "Handling Field Errors", there should be
	 * only one error per field. The returned field error may be:
	 * <ul>
	 * <li>on the field
	 * <li>on a parent field, when the field is not present
	 * <li>on a nested field, when a {@code non-null} nested field error bubbles up
	 * </ul>
	 * <p>As a fallback, this method also checks "request errors" in case the
	 * entire response is not {@link GraphQlResponse#isValid() valid}. If there
	 * are no errors at all, {@code null} is returned, and it implies the field
	 * value was set to {@code null} by its {@code DataFetcher}.
	 * <p>When the field <strong>does</strong> have a value, it is considered
	 * valid and this method returns {@code null}, although the field may be
	 * partial and contain {@link #getErrors() errors} on nested fields.
	 * @return return the error for this field, or {@code null} if there is no
	 * error with the same path as the field path
	 */
	@Nullable
	GraphQlResponseError getError();

	/**
	 * Return all field errors including errors above, at, and below this field.
	 * <p>In practice, when the field <strong>does have</strong> a value, it is
	 * considered valid but possibly partial with nested field errors. When the
	 * field <strong>does not have</strong> a value, there should be only one
	 * field error, and in that case it is better to use {@link #getError()}.
	 */
	List<GraphQlResponseError> getErrors();

}
