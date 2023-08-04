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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
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
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Declares an {@link #inspect(GraphQLSchema, RuntimeWiring)} method that checks
 * if schema mappings.
 *
 * <p>Schema mapping checks depend on {@code DataFetcher}s to be
 * {@link SelfDescribingDataFetcher} in order to compare schema type and Java
 * object type structure. If a {@code DataFetcher} does not implement this
 * interface, then the Java type remains unknown, and the field type is reported
 * as "skipped".
 *
 * <p>The {@code SelfDescribingDataFetcher} for an annotated controller method
 * derives type information from the controller method signature. If the declared
 * return type is {@link Object}, or an unspecified generic parameter such as
 * {@code List<?>} then the Java type structure remains unknown, and the field
 * output type is reported as skipped.
 *
 * <p>Unions are always skipped because there is no way for an annotated
 * controller method to express that in a return type, and the Java type
 * structure remains unknown.
 *
 * <p>Interfaces are supported only as far as fields declared directly on the
 * interface, which are compared against properties of the Java type declared
 * by a {@code SelfDescribingDataFetcher}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
final class SchemaMappingInspector {

	private static final Log logger = LogFactory.getLog(SchemaMappingInspector.class);


	private final GraphQLSchema schema;

	private final RuntimeWiring runtimeWiring;

	private final Set<String> inspectedTypes = new HashSet<>();

	private final ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

	private final ReportBuilder reportBuilder = new ReportBuilder();

	@Nullable
	private SchemaReport report;


	private SchemaMappingInspector(GraphQLSchema schema, RuntimeWiring runtimeWiring) {
		Assert.notNull(schema, "GraphQLSchema is required");
		Assert.notNull(runtimeWiring, "RuntimeWiring is required");
		this.schema = schema;
		this.runtimeWiring = runtimeWiring;
	}


	/**
	 * Perform an inspection and create a {@link SchemaReport}.
	 * The inspection is one once only, during the first call to this method.
	 */
	public SchemaReport getOrCreateReport() {
		if (this.report == null) {
			checkSchemaFields();
			checkDataFetcherRegistrations();
			this.report = this.reportBuilder.build();
		}
		return this.report;
	}

	private void checkSchemaFields() {

		checkFieldsContainer(this.schema.getQueryType(), null);

		if (this.schema.isSupportingMutations()) {
			checkFieldsContainer(this.schema.getMutationType(), null);
		}

		if (this.schema.isSupportingSubscriptions()) {
			checkFieldsContainer(this.schema.getSubscriptionType(), null);
		}
	}

	/**
	 * Check the given {@code GraphQLFieldsContainer} against {@code DataFetcher}
	 * registrations, or Java properties of the given {@code ResolvableType}.
	 * @param fieldContainer the GraphQL interface or object type to check
	 * @param resolvableType the Java type to match against, or {@code null} if
	 * not applicable such as for Query, Mutation, or Subscription
	 */
	@SuppressWarnings("rawtypes")
	private void checkFieldsContainer(GraphQLFieldsContainer fieldContainer, @Nullable ResolvableType resolvableType) {

		String typeName = fieldContainer.getName();
		Map<String, DataFetcher> dataFetcherMap = this.runtimeWiring.getDataFetcherForType(typeName);

		for (GraphQLFieldDefinition field : fieldContainer.getFieldDefinitions()) {
			String fieldName = field.getName();
			DataFetcher<?> dataFetcher = dataFetcherMap.get(fieldName);
			if (dataFetcher != null) {
				checkField(fieldContainer, field, dataFetcher);
			}
			else if (resolvableType == null || !hasProperty(resolvableType, fieldName)) {
				this.reportBuilder.unmappedField(FieldCoordinates.coordinates(typeName, fieldName));
			}
		}
	}

	/**
	 * Check the output {@link GraphQLType} of a field against the given DataFetcher return type.
	 * @param parent the parent of the field
	 * @param field the field to inspect
	 * @param dataFetcher the registered DataFetcher
	 */
	private void checkField(GraphQLFieldsContainer parent, GraphQLFieldDefinition field, DataFetcher<?> dataFetcher) {

		ResolvableType resolvableType = ResolvableType.NONE;
		if (dataFetcher instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher) {
			resolvableType = selfDescribingDataFetcher.getReturnType();
		}

		// Remove GraphQL type wrappers, and nest within Java generic types
		GraphQLType outputType = unwrapIfNonNull(field.getType());
		if (isPaginatedType(outputType)) {
			outputType = getPaginatedType((GraphQLObjectType) outputType);
			resolvableType = nestForConnection(resolvableType);
		}
		else if (outputType instanceof GraphQLList listType) {
			outputType = unwrapIfNonNull(listType.getWrappedType());
			resolvableType = nestForList(resolvableType, (parent == this.schema.getSubscriptionType()));
		}
		else {
			resolvableType = nestIfReactive(resolvableType);
		}

		// Type already inspected?
		if (addAndCheckIfAlreadyInspected(outputType)) {
			return;
		}

		// Can we inspect GraphQL type?
		if (!(outputType instanceof GraphQLFieldsContainer fieldContainer)) {
			if (isNotScalarOrEnumType(outputType)) {
				FieldCoordinates coordinates = FieldCoordinates.coordinates(parent.getName(), field.getName());
				addSkippedType(outputType, coordinates, "Unsupported schema type");
			}
			return;
		}

		// Can we inspect Java type?
		if (resolvableType.resolve(Object.class) == Object.class) {
			FieldCoordinates coordinates = FieldCoordinates.coordinates(parent.getName(), field.getName());
			addSkippedType(outputType, coordinates, "No Java type information");
			return;
		}

		// Nest within the
		checkFieldsContainer(fieldContainer, resolvableType);
	}

	private GraphQLType unwrapIfNonNull(GraphQLType type) {
		return type instanceof GraphQLNonNull graphQLNonNull ? graphQLNonNull.getWrappedType() : type;
	}

	private boolean isPaginatedType(GraphQLType type) {
		return type instanceof GraphQLObjectType objectType &&
				objectType.getName().endsWith("Connection") &&
				objectType.getField("edges") != null && objectType.getField("pageInfo") != null;
	}

	private GraphQLType getPaginatedType(GraphQLObjectType type) {
		String name = type.getName().substring(0, type.getName().length() - 10);
		GraphQLType nodeType = this.schema.getType(name);
		Assert.state(nodeType != null, "No node type for '" + type.getName() + "'");
		return nodeType;
	}

	private ResolvableType nestForConnection(ResolvableType type) {
		if (type == ResolvableType.NONE) {
			return type;
		}
		type = nestIfReactive(type);
		if (logger.isDebugEnabled() && type.getGenerics().length != 1) {
			logger.debug("Expected Connection type to have a generic parameter: " + type);
		}
		return type.getNested(2);
	}

	private ResolvableType nestIfReactive(ResolvableType type) {
		Class<?> clazz = type.resolve(Object.class);
		ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(clazz);
		if (adapter != null) {
			if (logger.isDebugEnabled() && adapter.isNoValue()) {
				logger.debug("Expected reactive/async return type that can produce value(s): " + type);
			}
			return type.getNested(2);
		}
		return type;
	}

	private ResolvableType nestForList(ResolvableType type, boolean subscription) {
		if (type == ResolvableType.NONE) {
			return type;
		}
		ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(type.resolve(Object.class));
		if (adapter != null) {
			if (logger.isDebugEnabled() && adapter.isNoValue()) {
				logger.debug("Expected List compatible type: " + type);
			}
			type = type.getNested(2);
			if (adapter.isMultiValue() && !subscription) {
				return type;
			}
		}
		if (logger.isDebugEnabled() && !type.isArray() && type.getGenerics().length != 1) {
			logger.debug("Expected List compatible type: " + type);
		}
		return type.getNested(2);
	}

	private static String typeNameToString(GraphQLType type) {
		return type instanceof GraphQLNamedType namedType ? namedType.getName() : type.toString();
	}

	private boolean addAndCheckIfAlreadyInspected(GraphQLType type) {
		return type instanceof GraphQLNamedOutputType outputType && !this.inspectedTypes.add(outputType.getName());
	}

	private static boolean isNotScalarOrEnumType(GraphQLType type) {
		return !(type instanceof GraphQLScalarType || type instanceof GraphQLEnumType);
	}

	private boolean hasProperty(ResolvableType resolvableType, String fieldName) {
		try {
			Class<?> clazz = resolvableType.resolve(Object.class);
			return BeanUtils.getPropertyDescriptor(clazz, fieldName) != null;
		}
		catch (BeansException ex) {
			throw new IllegalStateException(
					"Failed to introspect " + resolvableType + " for field '" + fieldName + "'", ex);
		}
	}

	private void addSkippedType(GraphQLType type, FieldCoordinates coordinates, String reason) {
		String typeName = typeNameToString(type);
		this.reportBuilder.skippedType(type, coordinates);
		if (logger.isDebugEnabled()) {
			logger.debug("Skipped '" + typeName + "': " + reason);
		}
	}

	@SuppressWarnings("rawtypes")
	private void checkDataFetcherRegistrations() {
		this.runtimeWiring.getDataFetchers().forEach((typeName, registrations) ->
				registrations.forEach((fieldName, dataFetcher) -> {
					FieldCoordinates coordinates = FieldCoordinates.coordinates(typeName, fieldName);
					if (this.schema.getFieldDefinition(coordinates) == null) {
						this.reportBuilder.unmappedRegistration(coordinates, dataFetcher);
					}
				}));
	}


	/**
	 * Check the schema against {@code DataFetcher} registrations, and produce a report.
	 * @param schema the schema to inspect
	 * @param runtimeWiring for {@code DataFetcher} registrations
	 * @return the created report
	 */
	public static SchemaReport inspect(GraphQLSchema schema, RuntimeWiring runtimeWiring) {
		return new SchemaMappingInspector(schema, runtimeWiring).getOrCreateReport();
	}


	/**
	 * Helps to build a {@link SchemaReport}.
	 */
	private class ReportBuilder {

		private final List<FieldCoordinates> unmappedFields = new ArrayList<>();

		private final Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations = new LinkedHashMap<>();

		private final List<SchemaReport.SkippedType> skippedTypes = new ArrayList<>();

		public void unmappedField(FieldCoordinates coordinates) {
			this.unmappedFields.add(coordinates);
		}

		public void unmappedRegistration(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
			this.unmappedRegistrations.put(coordinates, dataFetcher);
		}

		public void skippedType(GraphQLType type, FieldCoordinates coordinates) {
			this.skippedTypes.add(new DefaultSkippedType(type, coordinates));
		}

		public SchemaReport build() {
			return new DefaultSchemaReport(this.unmappedFields, this.unmappedRegistrations, this.skippedTypes);
		}

	}


	/**
	 * Default implementation of {@link SchemaReport}.
	 */
	private class DefaultSchemaReport implements SchemaReport {

		private final List<FieldCoordinates> unmappedFields;

		private final Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations;

		private final List<SchemaReport.SkippedType> skippedTypes;

		public DefaultSchemaReport(
				List<FieldCoordinates> unmappedFields, Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations,
				List<SkippedType> skippedTypes) {

			this.unmappedFields = Collections.unmodifiableList(unmappedFields);
			this.unmappedRegistrations = Collections.unmodifiableMap(unmappedRegistrations);
			this.skippedTypes = Collections.unmodifiableList(skippedTypes);
		}

		@Override
		public List<FieldCoordinates> unmappedFields() {
			return this.unmappedFields;
		}

		@Override
		public Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations() {
			return this.unmappedRegistrations;
		}

		@Override
		public List<SkippedType> skippedTypes() {
			return this.skippedTypes;
		}

		@Override
		public GraphQLSchema schema() {
			return SchemaMappingInspector.this.schema;
		}

		@Override
		@Nullable
		public DataFetcher<?> dataFetcher(FieldCoordinates coordinates) {
			return SchemaMappingInspector.this.runtimeWiring
					.getDataFetcherForType(coordinates.getTypeName())
					.get(coordinates.getFieldName());
		}

		@Override
		public String toString() {
			return "GraphQL schema inspection:\n" +
					"\tUnmapped fields: " + formatUnmappedFields() + "\n" +
					"\tUnmapped registrations: " + this.unmappedRegistrations + "\n" +
					"\tSkipped types: " + this.skippedTypes;
		}

		private String formatUnmappedFields() {
			MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
			this.unmappedFields.forEach(coordinates -> {
				List<String> fields = map.computeIfAbsent(coordinates.getTypeName(), s -> new ArrayList<>());
				fields.add(coordinates.getFieldName());
			});
			return map.toString();
		}

	}


	/**
	 * Default implementation of a {@link SchemaReport.SkippedType}.
	 */
	private record DefaultSkippedType(
			GraphQLType type, FieldCoordinates fieldCoordinates) implements SchemaReport.SkippedType {

		@Override
		public String toString() {
			return typeNameToString(this.type);
		}

	}

}
