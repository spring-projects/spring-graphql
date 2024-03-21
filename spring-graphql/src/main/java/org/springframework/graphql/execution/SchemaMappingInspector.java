/*
 * Copyright 2020-2024 the original author or authors.
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
import java.util.Optional;
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
import org.springframework.util.CollectionUtils;
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
@SuppressWarnings("rawtypes")
public class SchemaMappingInspector {

	private static final Log logger = LogFactory.getLog(SchemaMappingInspector.class);


	private final GraphQLSchema schema;

	private final Map<String, Map<String, DataFetcher>> dataFetchers;

	private final Set<String> inspectedTypes = new HashSet<>();

	private final ReportBuilder reportBuilder = new ReportBuilder();

	@Nullable
	private SchemaReport report;


	private SchemaMappingInspector(GraphQLSchema schema, Map<String, Map<String, DataFetcher>> dataFetchers) {
		Assert.notNull(schema, "GraphQLSchema is required");
		Assert.notNull(dataFetchers, "DataFetcher map is required");
		this.schema = schema;
		this.dataFetchers = dataFetchers;
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
	 * Check fields of the given {@code GraphQLFieldsContainer} to make sure there
	 * is either a {@code DataFetcher} registration, or a corresponding property
	 * in the given Java type, which may be {@code null} for the top-level types
	 * Query, Mutation, and Subscription.
	 */
	private void checkFieldsContainer(
			GraphQLFieldsContainer fieldContainer, @Nullable ResolvableType resolvableType) {

		String typeName = fieldContainer.getName();
		Map<String, DataFetcher> dataFetcherMap = this.dataFetchers.getOrDefault(typeName, Collections.emptyMap());

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
	 * Perform the following:
	 * <ul>
	 * <li>Resolve the field type and the {@code DataFetcher} return type, and recurse
	 * with {@link #checkFieldsContainer} if there is sufficient type information.
	 * <li>Resolve the arguments the {@code DataFetcher} depends on and check they
	 * are defined in the schema.
	 * </ul>
	 */
	private void checkField(
			GraphQLFieldsContainer parent, GraphQLFieldDefinition field, DataFetcher<?> dataFetcher) {

		if (dataFetcher instanceof SelfDescribingDataFetcher<?> selfDescribing) {
			checkFieldArguments(field, selfDescribing);
		}

		TypePair typePair = TypePair.resolveTypePair(parent, field, dataFetcher, schema);

		// Type already inspected?
		if (addAndCheckIfAlreadyInspected(typePair.outputType())) {
			return;
		}

		// Can we inspect GraphQL type?
		if (!(typePair.outputType() instanceof GraphQLFieldsContainer fieldContainer)) {
			if (isNotScalarOrEnumType(typePair.outputType())) {
				FieldCoordinates coordinates = FieldCoordinates.coordinates(parent.getName(), field.getName());
				addSkippedType(typePair.outputType(), coordinates, "Unsupported schema type");
			}
			return;
		}

		// Can we inspect Java type?
		if (typePair.resolvableType().resolve(Object.class) == Object.class) {
			FieldCoordinates coordinates = FieldCoordinates.coordinates(parent.getName(), field.getName());
			addSkippedType(typePair.outputType(), coordinates, "No Java type information");
			return;
		}

		checkFieldsContainer(fieldContainer, typePair.resolvableType());
	}

	private void checkFieldArguments(GraphQLFieldDefinition field, SelfDescribingDataFetcher<?> dataFetcher) {

		List<String> arguments = dataFetcher.getArguments().keySet().stream()
				.filter(name -> field.getArgument(name) == null)
				.toList();

		if (!arguments.isEmpty()) {
			this.reportBuilder.unmappedArgument(dataFetcher, arguments);
		}
	}



	private static String typeNameToString(GraphQLType type) {
		return (type instanceof GraphQLNamedType namedType ? namedType.getName() : type.toString());
	}

	private boolean addAndCheckIfAlreadyInspected(GraphQLType type) {
		return (type instanceof GraphQLNamedOutputType outputType && !this.inspectedTypes.add(outputType.getName()));
	}

	private static boolean isNotScalarOrEnumType(GraphQLType type) {
		return !(type instanceof GraphQLScalarType || type instanceof GraphQLEnumType);
	}

	private boolean hasProperty(ResolvableType resolvableType, String fieldName) {
		try {
			Class<?> clazz = resolvableType.resolve(Object.class);
			return (BeanUtils.getPropertyDescriptor(clazz, fieldName) != null);
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

	private void checkDataFetcherRegistrations() {
		this.dataFetchers.forEach((typeName, registrations) ->
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
		return inspect(schema, runtimeWiring.getDataFetchers());
	}

	/**
	 * Variant of {@link #inspect(GraphQLSchema, RuntimeWiring)} with a map of
	 * {@code DataFetcher} registrations.
	 * @since 1.2.5
	 */
	public static SchemaReport inspect(GraphQLSchema schema, Map<String, Map<String, DataFetcher>> dataFetchers) {
		return new SchemaMappingInspector(schema, dataFetchers).getOrCreateReport();
	}


	/**
	 * Container for a GraphQL and Java type pair along with logic to resolve the
	 * pair of types for a GraphQL field and the {@code DataFetcher} registered for it.
	 */
	private record TypePair(GraphQLType outputType, ResolvableType resolvableType) {

		private static final ReactiveAdapterRegistry adapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

		/**
		 * Given a GraphQL field and the {@link DataFetcher} registered for it, determine
		 * the type pair to use for schema inspection, removing list, non-null, and
		 * connection type wrappers, and nesting within generic types in order to get
		 * to the types to use for schema inspection.
		 * @param parent the parent type of the field
		 * @param field the field
		 * @param fetcher the {@code DataFetcher} registered for the field
		 * @param schema the GraphQL schema
		 * @return the GraphQL type and corresponding Java type, or {@link ResolvableType#NONE} if unresolved.
		 */
		public static TypePair resolveTypePair(
				GraphQLType parent, GraphQLFieldDefinition field, DataFetcher<?> fetcher, GraphQLSchema schema) {

			ResolvableType resolvableType =
					(fetcher instanceof SelfDescribingDataFetcher<?> sd ? sd.getReturnType() : ResolvableType.NONE);

			// Remove GraphQL type wrappers, and nest within Java generic types
			GraphQLType outputType = unwrapIfNonNull(field.getType());
			if (isPaginatedType(outputType)) {
				outputType = getPaginatedType((GraphQLObjectType) outputType, schema);
				resolvableType = nestForConnection(resolvableType);
			}
			else if (outputType instanceof GraphQLList listType) {
				outputType = unwrapIfNonNull(listType.getWrappedType());
				resolvableType = nestForList(resolvableType, parent == schema.getSubscriptionType());
			}
			else {
				resolvableType = nestIfWrappedType(resolvableType);
			}
			return new TypePair(outputType, resolvableType);
		}

		private static GraphQLType unwrapIfNonNull(GraphQLType type) {
			return (type instanceof GraphQLNonNull graphQLNonNull ? graphQLNonNull.getWrappedType() : type);
		}

		private static boolean isPaginatedType(GraphQLType type) {
			return (type instanceof GraphQLObjectType objectType &&
					objectType.getName().endsWith("Connection") &&
					objectType.getField("edges") != null && objectType.getField("pageInfo") != null);
		}

		private static GraphQLType getPaginatedType(GraphQLObjectType type, GraphQLSchema schema) {
			String name = type.getName().substring(0, type.getName().length() - 10);
			GraphQLType nodeType = schema.getType(name);
			Assert.state(nodeType != null, "No node type for '" + type.getName() + "'");
			return nodeType;
		}

		private static ResolvableType nestForConnection(ResolvableType type) {
			if (type == ResolvableType.NONE) {
				return type;
			}
			type = nestIfWrappedType(type);
			if (logger.isDebugEnabled() && type.getGenerics().length != 1) {
				logger.debug("Expected Connection type to have a generic parameter: " + type);
			}
			return type.getNested(2);
		}

		private static ResolvableType nestIfWrappedType(ResolvableType type) {
			Class<?> clazz = type.resolve(Object.class);
			if (Optional.class.isAssignableFrom(clazz)) {
				if (logger.isDebugEnabled() && type.getGeneric(0).resolve() == null) {
					logger.debug("Expected Optional type to have a generic parameter: " + type);
				}
				return type.getNested(2);
			}
			ReactiveAdapter adapter = adapterRegistry.getAdapter(clazz);
			if (adapter != null) {
				if (logger.isDebugEnabled() && adapter.isNoValue()) {
					logger.debug("Expected reactive/async return type that can produce value(s): " + type);
				}
				return type.getNested(2);
			}
			return type;
		}

		private static ResolvableType nestForList(ResolvableType type, boolean subscription) {
			if (type == ResolvableType.NONE) {
				return type;
			}
			ReactiveAdapter adapter = adapterRegistry.getAdapter(type.resolve(Object.class));
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

	};


	/**
	 * Helps to build a {@link SchemaReport}.
	 */
	private class ReportBuilder {

		private final List<FieldCoordinates> unmappedFields = new ArrayList<>();

		private final Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations = new LinkedHashMap<>();

		private final MultiValueMap<DataFetcher<?>, String> unmappedArguments = new LinkedMultiValueMap<>();

		private final List<SchemaReport.SkippedType> skippedTypes = new ArrayList<>();

		public void unmappedField(FieldCoordinates coordinates) {
			this.unmappedFields.add(coordinates);
		}

		public void unmappedRegistration(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
			this.unmappedRegistrations.put(coordinates, dataFetcher);
		}

		public void unmappedArgument(DataFetcher<?> dataFetcher, List<String> arguments) {
			this.unmappedArguments.put(dataFetcher, arguments);
		}

		public void skippedType(GraphQLType type, FieldCoordinates coordinates) {
			this.skippedTypes.add(new DefaultSkippedType(type, coordinates));
		}

		public SchemaReport build() {
			return new DefaultSchemaReport(
					this.unmappedFields, this.unmappedRegistrations, this.unmappedArguments, this.skippedTypes);
		}

	}


	/**
	 * Default implementation of {@link SchemaReport}.
	 */
	private class DefaultSchemaReport implements SchemaReport {

		private final List<FieldCoordinates> unmappedFields;

		private final Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations;

		private final MultiValueMap<DataFetcher<?>, String> unmappedArguments;

		private final List<SchemaReport.SkippedType> skippedTypes;

		public DefaultSchemaReport(
				List<FieldCoordinates> unmappedFields, Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations,
				MultiValueMap<DataFetcher<?>, String> unmappedArguments, List<SkippedType> skippedTypes) {

			this.unmappedFields = Collections.unmodifiableList(unmappedFields);
			this.unmappedRegistrations = Collections.unmodifiableMap(unmappedRegistrations);
			this.unmappedArguments = CollectionUtils.unmodifiableMultiValueMap(unmappedArguments);
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
		public MultiValueMap<DataFetcher<?>, String> unmappedArguments() {
			return this.unmappedArguments;
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
			return SchemaMappingInspector.this.dataFetchers
					.getOrDefault(coordinates.getTypeName(), Collections.emptyMap())
					.get(coordinates.getFieldName());
		}

		@Override
		public String toString() {
			return "GraphQL schema inspection:\n" +
					"\tUnmapped fields: " + formatUnmappedFields() + "\n" +
					"\tUnmapped registrations: " + this.unmappedRegistrations + "\n" +
					"\tUnmapped arguments: " + this.unmappedArguments + "\n" +
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
