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

import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import org.springframework.lang.Nullable;

/**
 * Default implementation of {@link TypeVisitorHelper} that performs checks
 * against {@link GraphQLSchema}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.1
 */
final class DefaultTypeVisitorHelper implements TypeVisitorHelper {

	@Nullable
	private final String subscriptionTypeName;


	/**
	 * Package private constructor
	 */
	DefaultTypeVisitorHelper(GraphQLSchema schema) {
		GraphQLObjectType subscriptionType = schema.getSubscriptionType();
		this.subscriptionTypeName = subscriptionType != null ? subscriptionType.getName() : null;
	}


	/**
	 * Whether the given type is the subscription type.
	 */
	@Override
	public boolean isSubscriptionType(GraphQLNamedType type) {
		return type.getName().equals(this.subscriptionTypeName);
	}

}
