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


import java.util.List;

import graphql.GraphQLError;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;

/**
 * Representation for a field in a GraphQL response, with options to examine its
 * value and errors, and to decode it.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ResponseField {

	/**
	 * Whether the field is valid. A field is invalid if:
	 * <ul>
	 * <li>the path doesn't exist
	 * <li>it is {@code null} AND has a field error
	 * </ul>
	 * <p>A field that is not {@code null} is valid but may still be partial
	 * with some fields below it set to {@code null} due to field errors.
	 * A valid field may be {@code null} if the schema allows it, but in that
	 * case it will not have any field errors.
	 */
	boolean isValid();

	/**
	 * Return the path for the field under the "data" key in the response map.
	 */
	String getPath();

	/**
	 * Return the field value without any decoding.
	 * @param <T> the expected value type, e.g. Map, List, or a scalar type.
	 * @return the value
	 */
	@Nullable
	<T> T getValue();

	/**
	 * Return the error for this field, if any. The error may be for this field
	 * when the field is {@code null}, or it may be for a parent field, when the
	 * current field does not exist.
	 * <p><strong>Note:</strong> The field error is identified by searching for
	 * the first error with a matching path that is shorter or the same as the
	 * field path. According to the GraphQL spec, section 6.4.4,
	 * "Handling Field Errors", there should be only one field error per field.
	 * @return return the error for this field, or {@code null} if there is no
	 * error with the same path as the field path
	 */
	@Nullable
	GraphQLError getError();

	/**
	 * Return all field errors including errors above, at, and below this field.
	 * <p>In practice, when the field has a value, all errors are for fields
	 * below. When the field does not have a value, there is only one error, and
	 * it is the same as {@link #getError()}.
	 */
	List<GraphQLError> getErrors();

	/**
	 * Decode the field to an entity of the given type.
	 * @param entityType the type to convert to
	 * @return the decoded entity, possibly {@code null} if the field
	 * {@link #getValue() value} is {@code null}
	 * @throws FieldAccessException if "this" field is not {@link #isValid() valid}
	 */
	@Nullable
	<D> D toEntity(Class<D> entityType);

	/**
	 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
	 */
	@Nullable
	<D> D toEntity(ParameterizedTypeReference<D> entityType);

	/**
	 * Decode the field to a list of entities with the given type.
	 * @param elementType the type of elements in the list
	 * @return the decoded list of entities, possibly empty
	 * @throws FieldAccessException if "this" field is not {@link #isValid() valid}
	 */
	<D> List<D> toEntityList(Class<D> elementType);

	/**
	 * Variant of {@link #toEntityList(Class)} with {@link ParameterizedTypeReference}.
	 */
	<D> List<D> toEntityList(ParameterizedTypeReference<D> elementType);

}
