/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.graphql.client.ClientGraphQlResponse;


/**
 * Representation for a field in a GraphQL response, with options to examine
 * the field value and errors.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ResponseField {

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
	@Nullable <T> T getValue();

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
