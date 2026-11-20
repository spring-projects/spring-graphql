/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * Callback that allows customizing the {@link TypeDefinitionRegistry} created
 * from parsed schema files. It allows adding schema types programmatically
 * before the registry is used to create {@link GraphQLSchema}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public interface TypeDefinitionConfigurer {

	/**
	 * Customize the given {@link TypeDefinitionRegistry}.
	 * @param registry the registry to customize
	 */
	void configure(TypeDefinitionRegistry registry);

}
