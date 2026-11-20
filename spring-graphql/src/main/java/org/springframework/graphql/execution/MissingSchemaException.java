/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import org.springframework.core.NestedRuntimeException;

/**
 * Indicates that no GraphQL schema definition was configured on the {@link GraphQlSource.Builder}.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class MissingSchemaException extends NestedRuntimeException {

	public MissingSchemaException() {
		super("No GraphQL schema definition was configured.");
	}
}
