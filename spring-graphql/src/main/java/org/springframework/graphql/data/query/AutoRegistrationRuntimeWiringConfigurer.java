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
package org.springframework.graphql.data.query;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import graphql.language.FieldDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.WiringFactory;

import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Given a map of GraphQL type names and DataFetcher factories, find queries
 * with a matching return type and register DataFetcher's for them, unless they
 * already have registrations.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class AutoRegistrationRuntimeWiringConfigurer implements RuntimeWiringConfigurer {

	private final Map<String, Function<Boolean, DataFetcher<?>>> dataFetcherFactories;


	public AutoRegistrationRuntimeWiringConfigurer(
			Map<String, Function<Boolean, DataFetcher<?>>> dataFetcherFactories) {

		this.dataFetcherFactories = dataFetcherFactories;
	}


	@Override
	public void configure(RuntimeWiring.Builder builder) {
	}

	@Override
	public void configure(RuntimeWiring.Builder builder, List<WiringFactory> container) {
		container.add(new AutoRegistrationWiringFactory(builder));
	}


	private class AutoRegistrationWiringFactory implements WiringFactory {

		private final RuntimeWiring.Builder builder;

		@Nullable
		private Predicate<String> existingQueryDataFetcherPredicate;

		AutoRegistrationWiringFactory(RuntimeWiring.Builder builder) {
			this.builder = builder;
		}

		@Override
		public boolean providesDataFetcher(FieldWiringEnvironment environment) {
			if (dataFetcherFactories.isEmpty()) {
				return false;
			}

			if (!environment.getParentType().getName().equals("Query")) {
				return false;
			}

			GraphQLType outputType = (environment.getFieldType() instanceof GraphQLList ?
					((GraphQLList) environment.getFieldType()).getWrappedType() :
					environment.getFieldType());

			String outputTypeName = (outputType instanceof GraphQLNamedOutputType ?
					((GraphQLNamedOutputType) outputType).getName() : null);

			return (outputTypeName != null &&
					dataFetcherFactories.containsKey(outputTypeName) &&
					!hasDataFetcherFor(environment.getFieldDefinition()));
		}

		private boolean hasDataFetcherFor(FieldDefinition fieldDefinition) {
			if (this.existingQueryDataFetcherPredicate == null) {
				Map<String, ?> map = this.builder.build().getDataFetcherForType("Query");
				this.existingQueryDataFetcherPredicate = fieldName -> map.get(fieldName) != null;
			}
			return this.existingQueryDataFetcherPredicate.test(fieldDefinition.getName());
		}

		@Override
		public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {
			return environment.getFieldType() instanceof GraphQLList ?
					initDataFetcher(((GraphQLList) environment.getFieldType()).getWrappedType(), false) :
					initDataFetcher(environment.getFieldType(), true);
		}

		private DataFetcher<?> initDataFetcher(GraphQLType type, boolean single) {
			Assert.isInstanceOf(GraphQLNamedOutputType.class, type);
			String typeName = ((GraphQLNamedOutputType) type).getName();
			Function<Boolean, DataFetcher<?>> factory = dataFetcherFactories.get(typeName);
			Assert.notNull(factory, "Expected DataFetcher factory");
			return factory.apply(single);
		}

	}

}
