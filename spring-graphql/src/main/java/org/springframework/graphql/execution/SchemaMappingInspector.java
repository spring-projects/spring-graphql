/*
 * Copyright 2020-2023 the original author or authors.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.RuntimeWiring;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Inspect the GraphQL schema and look for inconsistencies with declared {@code @SchemaMapping} handlers and {@link DataFetcher}.
 * The inspector will produce a {@link Report}, its content can be used for logging purposes.
 * <p>This inspection utility will report to developers:
 * <ul>
 *     <li>{@code Query}, {@code Mutation} and {@code Subscription} fields that have no corresponding {@link DataFetcher} registered
 *     <li>Fields in other schema types that have no property on the relevant Java type, or no DataFetcher registered
 * </ul>
 * <p>This approach has several known limitations; the corresponding Java types are only discovered through registered
 * {@code DataFetcher} instances, if they implement the {@link SelfDescribingDataFetcher} contract. Union types are not supported,
 * even if a common interface is declared by a {@link SelfDescribingDataFetcher}.
 *
 * @author Brian Clozel
 * @since 1.2.0
 */
class SchemaMappingInspector {

	private static final Log logger = LogFactory.getLog(SchemaMappingInspector.class);

	Report inspectSchemaMappings(GraphQLSchema schema, RuntimeWiring runtimeWiring) {
		ReportBuilder report = ReportBuilder.create();
		SchemaMappingInspection inspection = new SchemaMappingInspection(runtimeWiring);
		inspection.inspectOperation(schema.getQueryType(), report);
		inspection.inspectOperation(schema.getMutationType(), report);
		inspection.inspectOperation(schema.getSubscriptionType(), report);
		return report.build();
	}

	private static class SchemaMappingInspection {

		private final RuntimeWiring runtimeWiring;

		private final Set<String> seenTypes = new HashSet<>();

		SchemaMappingInspection(RuntimeWiring runtimeWiring) {
			this.runtimeWiring = runtimeWiring;
		}

		@SuppressWarnings("rawtypes")
		void inspectOperation(@Nullable GraphQLObjectType operationType, ReportBuilder report) {
			if (operationType != null) {
				Map<String, DataFetcher> operationDataFetchers = this.runtimeWiring.getDataFetcherForType(operationType.getName());
				for (GraphQLFieldDefinition fieldDefinition : operationType.getFieldDefinitions()) {
					if (operationDataFetchers.containsKey(fieldDefinition.getName())) {
						DataFetcher fieldDataFetcher = operationDataFetchers.get(fieldDefinition.getName());
						if (fieldDataFetcher instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher) {
							inspectType(fieldDefinition.getType(), selfDescribingDataFetcher.getReturnType(), report);
						}
					}
					else {
						report.missingOperation(operationType, fieldDefinition);
					}
				}
			}
		}

		private void inspectType(GraphQLType type, ResolvableType declaredType, ReportBuilder report) {
			if (type instanceof GraphQLObjectType objectType) {
				inspectObjectType(objectType, declaredType, report);
			}
			else if (type instanceof GraphQLList listType) {
				inspectType(listType.getWrappedType(), declaredType.getNested(2), report);
			}
			else if (type instanceof GraphQLNonNull nonNullType) {
				inspectType(nonNullType.getWrappedType(), declaredType, report);
			}
			else if (type instanceof GraphQLNamedType namedType && logger.isTraceEnabled()){
				logger.trace("Cannot inspect type '" + namedType.getName() + "', inspector does not support "
						+ type.getClass().getSimpleName());
			}
		}

		@SuppressWarnings("rawtypes")
		private void inspectObjectType(GraphQLObjectType objectType, ResolvableType declaredType, ReportBuilder report) {
			if (isTypeAlreadyInspected(objectType)) {
				return;
			}
			Map<String, DataFetcher> typeDataFetcher = this.runtimeWiring.getDataFetcherForType(objectType.getName());
			Class<?> declaredClass = unwrapPublisherTypes(declaredType);
			for (GraphQLFieldDefinition field : objectType.getFieldDefinitions()) {
				if (typeDataFetcher.containsKey(field.getName())) {
					DataFetcher fieldDataFetcher = typeDataFetcher.get(field.getName());
					if (fieldDataFetcher instanceof SelfDescribingDataFetcher<?> typedFieldDataFetcher) {
						inspectType(field.getType(), typedFieldDataFetcher.getReturnType(), report);
					}
				}
				else {
					try {
						if (declaredClass == null || BeanUtils.getPropertyDescriptor(declaredClass, field.getName()) == null) {
							report.missingField(objectType, field);
						}
					}
					catch (BeansException exc) {
						logger.debug("Failed while inspecting " + declaredType + " for property " + field.getName() + "", exc);
					}
				}
			}
		}

		@Nullable
		private Class<?> unwrapPublisherTypes(ResolvableType declaredType) {
			Class<?> rawClass = declaredType.getRawClass();
			if (rawClass != null) {
				ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(declaredType.getRawClass());
				if (adapter != null) {
					return declaredType.getNested(2).getRawClass();
				}
			}
			return rawClass;
		}

		private boolean isTypeAlreadyInspected(GraphQLObjectType objectType) {
			return !this.seenTypes.add(objectType.getName());
		}

	}

	record Report(MultiValueMap<String, String> missingOperations, MultiValueMap<String, String> missingFields) {

		String getSummary() {
			StringBuilder builder = new StringBuilder("GraphQL schema inspection found ");
			if (this.missingOperations.isEmpty() && this.missingFields.isEmpty()) {
				builder.append("no missing mapping.");
			}
			else {
			   builder.append(getDetailedReport());
			}
			return builder.toString();
		}

		private String getDetailedReport() {
			Stream<String> missingOperationsReport = this.missingOperations.keySet().stream()
					.map(operationName -> String.format("%s%s", operationName, this.missingOperations.get(operationName)));
			Stream<String> missingFieldsReport = this.missingFields.keySet().stream()
					.map(typeName -> String.format("%s%s", typeName, this.missingFields.get(typeName)));
			return Stream.concat(missingOperationsReport, missingFieldsReport)
					.collect(Collectors.joining(", ", "missing mappings for: ", "."));
		}

		boolean isEmpty() {
			return this.missingOperations.isEmpty() && this.missingFields.isEmpty();
		}

	}

	private static class ReportBuilder {

		private final MultiValueMap<String, String> missingOperations = new LinkedMultiValueMap<>();

		private final MultiValueMap<String, String> missingFields = new LinkedMultiValueMap<>();

		private ReportBuilder() {

		}

		static ReportBuilder create() {
			return new ReportBuilder();
		}

		ReportBuilder missingOperation(GraphQLObjectType operationType, GraphQLFieldDefinition operationDefinition) {
			this.missingOperations.add(operationType.getName(), operationDefinition.getName());
			return this;
		}

		ReportBuilder missingField(GraphQLObjectType objectType, GraphQLFieldDefinition field) {
			this.missingFields.add(objectType.getName(), field.getName());
			return this;
		}

		Report build() {
			return new Report(this.missingOperations, this.missingFields);
		}

	}

}
