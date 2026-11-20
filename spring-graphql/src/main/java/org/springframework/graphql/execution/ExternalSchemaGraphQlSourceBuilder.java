/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;


import graphql.schema.GraphQLSchema;

import org.springframework.util.Assert;


/**
 * {@link GraphQlSource.Builder} that uses an externally prepared
 * {@link GraphQLSchema}.
 *
 * @author Rossen Stoyanchev
 */
final class ExternalSchemaGraphQlSourceBuilder
		extends AbstractGraphQlSourceBuilder<ExternalSchemaGraphQlSourceBuilder>
		implements GraphQlSource.Builder<ExternalSchemaGraphQlSourceBuilder> {

	private final GraphQLSchema schema;


	ExternalSchemaGraphQlSourceBuilder(GraphQLSchema schema) {
		Assert.notNull(schema, "GraphQLSchema is required");
		this.schema = schema;
	}


	@Override
	protected GraphQLSchema initGraphQlSchema() {
		return this.schema;
	}

}
