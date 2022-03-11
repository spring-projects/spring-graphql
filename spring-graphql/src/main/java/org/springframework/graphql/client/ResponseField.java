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
	 * Return errors with paths matching that of the field.
	 */
	List<GraphQLError> getErrorsAt();

	/**
	 * Return errors with paths below that of the field.
	 */
	List<GraphQLError> getErrorsBelow();

	/**
	 * Decode the field to an entity of the given type.
	 * @param entityType the type to convert to
	 * @return the entity instance
	 */
	<D> D toEntity(Class<D> entityType);

	/**
	 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
	 * @param entityType the type to convert to
	 * @return the entity instance
	 */
	<D> D toEntity(ParameterizedTypeReference<D> entityType);

	/**
	 * Decode the field to a list of entities with the given type.
	 * @param elementType the type of elements in the list
	 * @return the list of entities
	 */
	<D> List<D> toEntityList(Class<D> elementType);

	/**
	 * Variant of {@link #toEntityList(Class)} with {@link ParameterizedTypeReference}.
	 * @param elementType the type of elements in the list
	 * @return the list of entities
	 */
	<D> List<D> toEntityList(ParameterizedTypeReference<D> elementType);

}
