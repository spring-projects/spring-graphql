/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;


import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlResponse;
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
	 * @param <D> the entity type
	 * @param entityType the type to convert to
	 * @return the decoded entity, or {@code null} if the field is {@code null}
	 * but otherwise there are no errors
	 * @throws FieldAccessException if the target field is {@code null} and the
	 * response is not {@link GraphQlResponse#isValid() valid} or the field has
	 * {@link ResponseField#getErrors() errors}.
	 */
	@Nullable <D> D toEntity(Class<D> entityType);

	/**
	 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
	 * @param <D> the entity type
	 * @param entityType the type to convert to
	 */
	@Nullable <D> D toEntity(ParameterizedTypeReference<D> entityType);

	/**
	 * Variant of {@link #toEntity(Class)} to decode to a list of entities.
	 * @param <D> the entity type
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
	 * @param <D> the entity type
	 * @param elementType the type of elements in the list
	 * @return the list of decoded entities, or an empty list if the field is
	 * {@code null} but otherwise there are no errors
	 * @throws FieldAccessException if the target field is {@code null} and the
	 * response is not {@link GraphQlResponse#isValid() valid} or the field has
	 * {@link ResponseField#getErrors() errors}.
	 */
	<D> List<D> toEntityList(ParameterizedTypeReference<D> elementType);

}
