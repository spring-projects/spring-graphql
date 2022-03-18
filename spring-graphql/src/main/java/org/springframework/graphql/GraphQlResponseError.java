package org.springframework.graphql;

import java.util.List;
import java.util.Map;

import graphql.ErrorClassification;
import graphql.language.SourceLocation;

import org.springframework.lang.Nullable;

/**
 * Represents a GraphQL response error.
 *
 * @author Rossen Stoyanchev
 * @since 1.0
 */
public interface GraphQlResponseError {

	/**
	 * Return the message with a description of the error intended for the
	 * developer as a guide to understand and correct the error.
	 */
	@Nullable
	String getMessage();

	/**
	 * Return a list of locations in the GraphQL document, if the error can be
	 * associated to a particular point in the document. Each location has a
	 * line and a column, both positive, starting from 1 and describing the
	 * beginning of an associated syntax element.
	 */
	List<SourceLocation> getLocations();

	/**
	 * Return a classification for the error that is specific to GraphQL Java.
	 * This is serialized under {@link #getExtensions() "extensions"} in the
	 * response map.
	 * @see graphql.ErrorType
	 * @see org.springframework.graphql.execution.ErrorType
	 */
	@Nullable
	ErrorClassification getErrorType();

	/**
	 * Return the path to a response field which experienced the error,
	 * if the error can be associated to a particular field in the result. This
	 * allows a client to identify whether a {@code null} result is intentional
	 * or caused by an error.
	 * <p>This list contains path segments starting at the root of the response
	 * and ending with the field associated with the error. Path segments that
	 * represent fields are strings, and path segments that represent list
	 * indices are 0-indexed integers. If the error happens in an aliased field,
	 * the path uses the aliased name, since it represents a path in the
	 * response, not in the request.
	 */
	@Nullable
	List<Object> getPath();

	/**
	 * Return a map with GraphQL Java specific error details such as the
	 * {@link #getErrorType()}.
	 */
	@Nullable
	Map<String, Object> getExtensions();

}
