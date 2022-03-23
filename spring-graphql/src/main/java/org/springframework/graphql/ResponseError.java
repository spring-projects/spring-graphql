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
public interface ResponseError {

	/**
	 * Return the message with a description of the error intended for the
	 * developer as a guide to understand and correct the error.
	 */
	@Nullable
	String getMessage();

	/**
	 * Return a classification for the error that is specific to GraphQL Java.
	 * This is serialized under {@link #getExtensions() "extensions"} in the
	 * response map.
	 * @see graphql.ErrorType
	 * @see org.springframework.graphql.execution.ErrorType
	 */
	ErrorClassification getErrorType();

	/**
	 * Return a String representation of the {@link #getParsedPath() parsed path},
	 * or an empty String if the error is not associated with a field.
	 * <p>Example paths:
	 * <pre>
	 * "hero"
	 * "hero.name"
	 * "hero.friends"
	 * "hero.friends[2]"
	 * "hero.friends[2].name"
	 * </pre>
	 *
	 */
	String getPath();

	/**
	 * Return the path to a response field which experienced the error,
	 * if the error can be associated to a particular field in the result, or
	 * otherwise an empty list. This allows a client to identify whether a
	 * {@code null} result is intentional or caused by an error.
	 * <p>This list contains path segments starting at the root of the response
	 * and ending with the field associated with the error. Path segments that
	 * represent fields are strings, and path segments that represent list
	 * indices are 0-indexed integers. If the error happens in an aliased field,
	 * the path uses the aliased name, since it represents a path in the
	 * response, not in the request.
	 */
	List<Object> getParsedPath();

	/**
	 * Return a list of locations in the GraphQL document, if the error can be
	 * associated to a particular point in the document. Each location has a
	 * line and a column, both positive, starting from 1 and describing the
	 * beginning of an associated syntax element.
	 */
	List<SourceLocation> getLocations();

	/**
	 * Return a map with GraphQL Java and other implementation specific protocol
	 * error detail extensions such as {@link #getErrorType()}, possibly empty.
	 */
	Map<String, Object> getExtensions();

}
