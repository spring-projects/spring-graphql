/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;


import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlResponse;

/**
 * {@link GraphQlResponse} for client use, with further options to handle the
 * response.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ClientGraphQlResponse extends GraphQlResponse {

	/**
	 * {@inheritDoc}
	 */
	@Override
	ClientResponseField field(String path);

	/**
	 * Decode the full response map to the given target type.
	 * @param <D> the target type
	 * @param type the target class
	 * @return the decoded value, or never {@code null}
	 * @throws FieldAccessException if the response is not {@link #isValid() valid}
	 */
	<D> D toEntity(Class<D> type);

	/**
	 * Variant of {@link #toEntity(Class)} with a {@link ParameterizedTypeReference}.
	 * @param <D> the target type
	 * @param type the target parameterized type
	 * @return the decoded value, or never {@code null}
	 * @throws FieldAccessException if the response is not {@link #isValid() valid}
	 */
	<D> D toEntity(ParameterizedTypeReference<D> type);

}
