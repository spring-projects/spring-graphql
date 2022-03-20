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
import org.springframework.graphql.ResponseField;

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
	 * @return the decoded entity, never {@code null}
	 * @throws FieldAccessException if the target field is not present or
	 * has no value, checked via {@link #hasValue()}.
	 */
	<D> D toEntity(Class<D> entityType);

	/**
	 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
	 */
	<D> D toEntity(ParameterizedTypeReference<D> entityType);

	/**
	 * Decode the field to a list of entities with the given type.
	 * @param elementType the type of elements in the list
	 * @return the decoded list of entities, possibly empty
	 * @throws FieldAccessException if the target field is not present or
	 * has no value, checked via {@link #hasValue()}.
	 */
	<D> List<D> toEntityList(Class<D> elementType);

	/**
	 * Variant of {@link #toEntityList(Class)} with {@link ParameterizedTypeReference}.
	 */
	<D> List<D> toEntityList(ParameterizedTypeReference<D> elementType);

}
