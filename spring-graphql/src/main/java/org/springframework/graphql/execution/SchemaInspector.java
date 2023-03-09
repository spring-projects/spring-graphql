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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.SDLExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.TypedDataFetcher;
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
 * {@code DataFetcher} instances, if they implement the {@link TypedDataFetcher} contract. Union types are not supported,
 * even if a common interface is declared by a {@link TypedDataFetcher}.
 *
 * @author Brian Clozel
 * @since 1.2.0
 */
class SchemaInspector {

	private static final Log logger = LogFactory.getLog(SchemaInspector.class);

	Report inspectSchema(TypeDefinitionRegistry typeDefinitionRegistry, RuntimeWiring runtimeWiring) {
		ReportBuilder report = ReportBuilder.create();
		SchemaInspection inspection = new SchemaInspection(typeDefinitionRegistry, runtimeWiring);
		inspection.inspectOperation("Query", report);
		inspection.inspectOperation("Mutation", report);
		inspection.inspectOperation("Subscription", report);
		return report.build();
	}

	private static class SchemaInspection {

		private final TypeDefinitionRegistry typeDefinitionRegistry;

		private final RuntimeWiring runtimeWiring;

		private final Set<String> seenTypes = new HashSet<>();

		SchemaInspection(TypeDefinitionRegistry typeDefinitionRegistry, RuntimeWiring runtimeWiring) {
			this.typeDefinitionRegistry = typeDefinitionRegistry;
			this.runtimeWiring = runtimeWiring;
		}

		@SuppressWarnings("rawtypes")
		void inspectOperation(String operationName, ReportBuilder report) {
			Map<String, DataFetcher> queryFetchers = this.runtimeWiring.getDataFetcherForType(operationName);
			this.typeDefinitionRegistry.getType(operationName, ObjectTypeDefinition.class)
					.ifPresent(queryType -> inspectOperation(queryType, queryFetchers, report));
			forEachObjectTypeExtension(operationName, objectTypeExtension -> inspectOperation(objectTypeExtension, queryFetchers, report));
		}

		@SuppressWarnings("rawtypes")
		private void inspectOperation(ObjectTypeDefinition operationDefinition, Map<String, DataFetcher> operationDataFetchers, ReportBuilder report) {
			for (FieldDefinition fieldDefinition : operationDefinition.getFieldDefinitions()) {
				if (operationDataFetchers.containsKey(fieldDefinition.getName())) {
					DataFetcher fieldDataFetcher = operationDataFetchers.get(fieldDefinition.getName());
					if (fieldDataFetcher instanceof TypedDataFetcher<?> typedDataFetcher) {
						inspectType(fieldDefinition.getType(), typedDataFetcher.getDeclaredType(), report);
					}
				}
				else {
					report.missingOperation(operationDefinition, fieldDefinition);
				}
			}
		}

		private void inspectType(Type<?> fieldType, ResolvableType declaredType, ReportBuilder report) {
			if (fieldType instanceof TypeName typeName) {
				this.typeDefinitionRegistry.getType(typeName)
						.ifPresent(typeDefinition -> inspectTypeDefinition(typeDefinition, declaredType, report));
				forEachObjectTypeExtension(typeName.getName(),
						objectTypeExtension -> inspectTypeDefinition(objectTypeExtension, declaredType, report));
			}
			else if (fieldType instanceof ListType listType) {
				inspectType(listType.getType(), declaredType.getNested(2), report);
			}
			else if (fieldType instanceof NonNullType nonNullType) {
				inspectType(nonNullType.getType(), declaredType, report);
			}
		}

		private void inspectTypeDefinition(TypeDefinition<?> typeDefinition, ResolvableType declaredType, ReportBuilder report) {
			if (typeDefinition instanceof ImplementingTypeDefinition<?> implementingTypeDefinition) {
				inspectImplementingType(implementingTypeDefinition, declaredType, report);
			}
			else if (logger.isDebugEnabled()){
				logger.debug("Cannot inspect type '" + typeDefinition.getName() + "', inspector does not support "
						+ typeDefinition.getClass().getSimpleName());
			}
		}

		@SuppressWarnings("rawtypes")
		private void inspectImplementingType(ImplementingTypeDefinition<?> typeDefinition, ResolvableType declaredType, ReportBuilder report) {
			if (isTypeAlreadyInspected(typeDefinition)) {
				return;
			}
			Map<String, DataFetcher> typeDataFetcher = this.runtimeWiring.getDataFetcherForType(typeDefinition.getName());
			Class<?> declaredClass = unwrapPublisherTypes(declaredType);
			for (FieldDefinition field : typeDefinition.getFieldDefinitions()) {
				if (typeDataFetcher.containsKey(field.getName())) {
					DataFetcher fieldDataFetcher = typeDataFetcher.get(field.getName());
					if (fieldDataFetcher instanceof TypedDataFetcher<?> typedFieldDataFetcher) {
						inspectType(field.getType(), typedFieldDataFetcher.getDeclaredType(), report);
					}
				}
				else {
					try {
						if (declaredClass == null || BeanUtils.getPropertyDescriptor(declaredClass, field.getName()) == null) {
							report.missingField(typeDefinition, field);
						}
					}
					catch (BeansException exc) {
						logger.debug("Failed while inspecting " + declaredType + " for property " + field.getName() + "", exc);
					}
				}
			}
			for (Type interfaceType : typeDefinition.getImplements()) {
				inspectType(interfaceType, declaredType, report);
			}
		}

		private void forEachObjectTypeExtension(String typeName, Consumer<ObjectTypeExtensionDefinition> extensionsConsumer) {
			List<ObjectTypeExtensionDefinition> objectTypeExtensions = this.typeDefinitionRegistry.objectTypeExtensions().get(typeName);
			if (objectTypeExtensions != null) {
				objectTypeExtensions.forEach(extensionsConsumer);
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

		private boolean isTypeAlreadyInspected(ImplementingTypeDefinition<?> typeDefinition) {
			if (typeDefinition instanceof SDLExtensionDefinition) {
				return false;
			}
			boolean inspectedType = this.seenTypes.contains(typeDefinition.getName());
			if (!inspectedType) {
				this.seenTypes.add(typeDefinition.getName());
			}
			return inspectedType;
		}

	}

	record Report(MultiValueMap<String, String> missingOperations, MultiValueMap<String, String> missingFields) {

		String getSummary() {
			StringBuilder builder = new StringBuilder("GraphQL schema inspection found ");
			if (this.missingOperations.isEmpty()) {
				builder.append("no missing mappings for operations");
			}
			else {
				builder.append("missing mappings for ").append(this.missingOperations.keySet());
			}
			if (this.missingFields.isEmpty()) {
				builder.append(", no missing data fetchers for inspected types.");
			}
			else {
				builder.append(", missing data fetchers for types ").append(this.missingFields.keySet()).append('.');
			}
			return builder.toString();
		}

		String getDetailedReport() {
			StringBuilder builder = new StringBuilder();
			this.missingOperations.keySet().forEach(operationName -> {
				builder.append(String.format("- on %s: %s", operationName, this.missingOperations.get(operationName)))
						.append(System.lineSeparator());
			});
			this.missingFields.keySet().forEach(typeName -> {
				builder.append(String.format("- on %s: %s", typeName, this.missingFields.get(typeName)))
						.append(System.lineSeparator());
			});
			return builder.toString();
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

		ReportBuilder missingOperation(ImplementingTypeDefinition<?> operationType, FieldDefinition operationDefinition) {
			this.missingOperations.add(operationType.getName(), operationDefinition.getName());
			return this;
		}

		ReportBuilder missingField(ImplementingTypeDefinition<?> type, FieldDefinition field) {
			this.missingFields.add(type.getName(), field.getName());
			return this;
		}

		Report build() {
			return new Report(this.missingOperations, this.missingFields);
		}

	}

}
