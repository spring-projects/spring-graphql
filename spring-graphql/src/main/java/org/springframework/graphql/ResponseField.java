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
public interface ResponseField {

	/**
	 * Whether the field has a value.
	 * <ul>
	 * <li>{@code "true"} means the field is not {@code null}, and therefore valid,
	 * although it may be partial with nested field {@link #getErrors() errors}.
	 * <li>{@code "false"} means the field is {@code null} or doesn't exist; use
	 * {@link #getErrors()} to check for field errors, and
	 * {@link GraphQlResponse#isValid()} to check if the entire response is
	 * valid or not.
	 * </ul>
	 * @deprecated as of 1.0.3 in favor of checking via {@link #getValue()}
	 */
	@Deprecated
	boolean hasValue();

	/**
	 * Return a String representation of the field path as described in
	 * {@link ClientGraphQlResponse#field(String)}.
	 */
	String getPath();

	/**
	 * Return a parsed representation of the field path, in the format described
	 * for error paths in Section 7.1.2, "Response Format" of the GraphQL spec.
	 * @see ResponseError#getParsedPath()
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
	 * <p>When the field is {@code null}, this method looks for the first field
	 * error. According to the GraphQL spec, section 6.4.4, "Handling Field
	 * Errors", there should be only one error per field. The returned field
	 * error may be:
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
	 * @deprecated since 1.0.3 in favor of {@link #getErrors()}
	 */
	@Nullable
	@Deprecated
	ResponseError getError();

	/**
	 * Return all errors that have a path, and it is at above, or below the field path.
	 * <p>According to the GraphQL spec, section 6.4.4, "Handling Field Errors"
	 * if a field has an error it is set to {@code null}. That means a field
	 * has either a value or an error, and there is only one error per field.
	 * <p>Errors may also occur at paths above or below the field path. Consider
	 * the following cases:
	 * <table>
	 * <tr>
	 * <th>Value</th>
	 * <th>Errors</th>
	 * <th>Case</th>
	 * </tr>
	 * <tr>
	 * <td>Non-{@code null}</td>
	 * <td>Empty</td>
	 * <td>Success</td>
	 * </tr>
	 * <tr>
	 * <td>Non-{@code null}</td>
	 * <td>Errors below</td>
	 * <td>Partial with errors on nested fields</td>
	 * </tr>
	 * <tr>
	 * <td>{@code null}</td>
	 * <td>Error at field</td>
	 * <td>Field failure</td>
	 * </tr>
	 * <tr>
	 * <td>{@code null}</td>
	 * <td>Error above field</td>
	 * <td>Parent field failure</td>
	 * </tr>
	 * <tr>
	 * <td>{@code null}</td>
	 * <td>Error below field</td>
	 * <td>Nested field failure bubbles up because field is required</td>
	 * </tr>
	 * </table>
	 */
	List<ResponseError> getErrors();

}
