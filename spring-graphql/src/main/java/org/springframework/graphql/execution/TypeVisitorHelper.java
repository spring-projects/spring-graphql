/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.graphql.execution;

import java.util.List;

import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;

/**
 * Helper for {@link graphql.schema.GraphQLTypeVisitor}s registered via
 * {@link GraphQlSource.Builder#typeVisitors(List)} that is exposed as a
 * variable in {@link graphql.util.TraverserContext}.
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
		return new DefaultTypeVisitorHelper(schema);
	}

}
