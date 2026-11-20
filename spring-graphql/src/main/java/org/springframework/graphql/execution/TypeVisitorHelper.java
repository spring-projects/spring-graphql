/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;

/**
 * Helps {@link graphql.schema.GraphQLTypeVisitor}s to recognize whether a type
 * is the subscription type. Exposed as a variable in
 * {@link graphql.util.TraverserContext}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.1
 */
public interface TypeVisitorHelper {

	/**
	 * Whether the given type is the subscription type.
	 * @param type the GraphQL type to check
	 */
	boolean isSubscriptionType(GraphQLNamedType type);


	/**
	 * Create an instance with the given {@link GraphQLSchema}.
	 * @param schema the GraphQL schema to use
	 */
	static TypeVisitorHelper create(GraphQLSchema schema) {
		String name = (schema.getSubscriptionType() != null) ? schema.getSubscriptionType().getName() : null;
		return (candidate) -> candidate.getName().equals(name);
	}

}
