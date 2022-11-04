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

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseField;
import org.springframework.lang.Nullable;

/**
 * Extends {@link ResponseField} to add options for decoding the field value.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ClientResponseField extends ResponseField {

	/**
	 * Decode the field to an entity of the given type.
	 * @param entityType the type to convert to
	 * @return the decoded entity, or {@code null} if the field is {@code null}
	 * but otherwise there are no errors
	 * @throws FieldAccessException if the target field is {@code null} and the
	 * response is not {@link GraphQlResponse#isValid() valid} or the field has
	 * {@link ResponseField#getErrors() errors}.
	 */
	@Nullable
	<D> D toEntity(Class<D> entityType);

	/**
	 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
	 */
	@Nullable
	<D> D toEntity(ParameterizedTypeReference<D> entityType);

	/**
	 * Variant of {@link #toEntity(Class)} to decode to a list of entities.
	 * @param elementType the type of elements in the list
	 * @return the list of decoded entities, or an empty list if the field is
	 * {@code null} but otherwise there are no errors
	 * @throws FieldAccessException if the target field is {@code null} and the
	 * response is not {@link GraphQlResponse#isValid() valid} or the field has
	 * {@link ResponseField#getErrors() errors}.
	 */
	<D> List<D> toEntityList(Class<D> elementType);

		/**
		 * Variant of {@link #toEntity(Class)} to decode to a list of entities.
		 * @param elementType the type of elements in the list
		 * @return the list of decoded entities, or an empty list if the field is
		 * {@code null} but otherwise there are no errors
		 * @throws FieldAccessException if the target field is {@code null} and the
		 * response is not {@link GraphQlResponse#isValid() valid} or the field has
		 * {@link ResponseField#getErrors() errors}.
		 */
	<D> List<D> toEntityList(ParameterizedTypeReference<D> elementType);

}
