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
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.WiringFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

	private final static Log logger = LogFactory.getLog(AutoRegistrationRuntimeWiringConfigurer.class);


	private final Map<String, Function<Boolean, DataFetcher<?>>> dataFetcherFactories;


	/**
	 * Constructor with a Map of GraphQL type names for which auto-registration
	 * can be performed.
	 * @param dataFetcherFactories Map with GraphQL type names as keys, and
	 * functions to create a corresponding {@link DataFetcher} as values.
	 */
	AutoRegistrationRuntimeWiringConfigurer(
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

			String outputTypeName = getOutputTypeName(environment);

			boolean result = (outputTypeName != null &&
					dataFetcherFactories.containsKey(outputTypeName) &&
					!hasDataFetcherFor(environment.getFieldDefinition()));

			if (!result) {
				// This may be called multiples times on success, so log only rejections from here
				logTraceMessage(environment, outputTypeName, false);
			}

			return result;
		}

		@Nullable
		private String getOutputTypeName(FieldWiringEnvironment environment) {
			GraphQLType outputType = (environment.getFieldType() instanceof GraphQLList ?
					((GraphQLList) environment.getFieldType()).getWrappedType() :
					environment.getFieldType());

			if (outputType instanceof GraphQLNonNull) {
				outputType = ((GraphQLNonNull) outputType).getWrappedType();
			}

			return (outputType instanceof GraphQLNamedOutputType ?
					((GraphQLNamedOutputType) outputType).getName() : null);
		}

		private boolean hasDataFetcherFor(FieldDefinition fieldDefinition) {
			if (this.existingQueryDataFetcherPredicate == null) {
				Map<String, ?> map = this.builder.build().getDataFetcherForType("Query");
				this.existingQueryDataFetcherPredicate = fieldName -> map.get(fieldName) != null;
			}
			return this.existingQueryDataFetcherPredicate.test(fieldDefinition.getName());
		}

		private void logTraceMessage(
				FieldWiringEnvironment environment, @Nullable String outputTypeName, boolean match) {

			if (logger.isTraceEnabled()) {
				String query = environment.getFieldDefinition().getName();
				logger.trace((match ? "Matched" : "Skipped") +
						" output typeName " + (outputTypeName != null ? "'" + outputTypeName + "'" : "null") +
						" for query '" + query + "'");
			}
		}

		@Override
		public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {

			String outputTypeName = getOutputTypeName(environment);
			logTraceMessage(environment, outputTypeName, true);

			Function<Boolean, DataFetcher<?>> factory = dataFetcherFactories.get(outputTypeName);
			Assert.notNull(factory, "Expected DataFetcher factory for typeName '" + outputTypeName + "'");

			boolean single = !(environment.getFieldType() instanceof GraphQLList);
			return factory.apply(single);
		}

	}

}
