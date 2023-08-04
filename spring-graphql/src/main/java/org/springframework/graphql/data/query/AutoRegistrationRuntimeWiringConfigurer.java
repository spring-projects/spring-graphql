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
package org.springframework.graphql.data.query;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import graphql.language.FieldDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
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

	private static final Log logger = LogFactory.getLog(AutoRegistrationRuntimeWiringConfigurer.class);


	private final Map<String, DataFetcherFactory> dataFetcherFactories;


	/**
	 * Constructor with a Map of GraphQL type names as keys, and
	 * {@code DataFetcher} factories as values.
	 */
	AutoRegistrationRuntimeWiringConfigurer(Map<String, DataFetcherFactory> factories) {
		this.dataFetcherFactories = factories;
	}


	@Override
	public void configure(RuntimeWiring.Builder builder) {
	}

	@Override
	public void configure(RuntimeWiring.Builder builder, List<WiringFactory> container) {
		container.add(new AutoRegistrationWiringFactory(builder));
	}


	/**
	 * Callback interface to create the desired type of {@code DataFetcher}.
	 */
	interface DataFetcherFactory {

		/**
		 * Create {@code DataFetcher} for a singe item.
		 */
		DataFetcher<?> single();

		/**
		 * Create {@code DataFetcher} for many items.
		 */
		DataFetcher<?> many();

		/**
		 * Create {@code DataFetcher} for scrolling.
		 */
		DataFetcher<?> scrollable();

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

			if (!"Query".equals(environment.getParentType().getName())) {
				return false;
			}

			String outputTypeName = getOutputTypeName(environment);

			boolean result = outputTypeName != null &&
					dataFetcherFactories.containsKey(outputTypeName) &&
					!hasDataFetcherFor(environment.getFieldDefinition());

			if (!result) {
				// This may be called multiples times on success, so log only rejections from here
				logTraceMessage(environment, outputTypeName, false);
			}

			return result;
		}

		@Nullable
		private String getOutputTypeName(FieldWiringEnvironment environment) {
			GraphQLType outputType = removeNonNullWrapper(environment.getFieldType());

			if (isConnectionType(outputType)) {
				String name = ((GraphQLObjectType) outputType).getName();
				return name.substring(0, name.length() - 10);
			}

			if (outputType instanceof GraphQLList) {
				outputType = removeNonNullWrapper(((GraphQLList) outputType).getWrappedType());
			}

			if (outputType instanceof GraphQLNamedOutputType namedType) {
				return namedType.getName();
			}

			return null;
		}

		private GraphQLType removeNonNullWrapper(GraphQLType outputType) {
			return outputType instanceof GraphQLNonNull wrapper ? wrapper.getWrappedType() : outputType;
		}

		private boolean isConnectionType(GraphQLType type) {
			return type instanceof GraphQLObjectType objectType &&
					objectType.getName().endsWith("Connection") &&
					objectType.getField("edges") != null && objectType.getField("pageInfo") != null;
		}

		private boolean hasDataFetcherFor(FieldDefinition fieldDefinition) {
			if (this.existingQueryDataFetcherPredicate == null) {
				Map<String, ?> map = this.builder.build().getDataFetcherForType("Query");
				this.existingQueryDataFetcherPredicate = fieldName -> map.get(fieldName) != null;
			}
			return this.existingQueryDataFetcherPredicate.test(fieldDefinition.getName());
		}

		private void logTraceMessage(FieldWiringEnvironment environment, @Nullable String typeName, boolean match) {
			if (logger.isTraceEnabled()) {
				String query = environment.getFieldDefinition().getName();
				logger.trace((match ? "Matched" : "Skipped") +
						" output typeName " + (typeName != null ? "'" + typeName + "'" : "null") +
						" for query '" + query + "'");
			}
		}

		@Override
		public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {

			String outputTypeName = getOutputTypeName(environment);
			logTraceMessage(environment, outputTypeName, true);

			DataFetcherFactory factory = dataFetcherFactories.get(outputTypeName);
			Assert.notNull(factory, "Expected DataFetcher factory for typeName '" + outputTypeName + "'");

			GraphQLType type = removeNonNullWrapper(environment.getFieldType());
			return isConnectionType(type) ? factory.scrollable() :
					(type instanceof GraphQLList ? factory.many() : factory.single());
		}

	}

}
