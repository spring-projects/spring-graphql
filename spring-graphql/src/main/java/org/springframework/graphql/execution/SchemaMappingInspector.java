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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
 * if schema fields are covered either by a {@link DataFetcher} registration,
 * or match a Java object property. Fields that have neither are reported as
 * "unmapped" in the resulting {@link SchemaMappingReport}. The inspection also
 * performs a reverse check for {@code DataFetcher} registrations against schema
 * fields that don't exist.
 *
 * <p>The schema field inspection depends on {@code DataFetcher}s to be
 * {@link SelfDescribingDataFetcher} to be able to compare schema type and Java
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

	private final MultiValueMap<String, String> unmappedFields = new LinkedMultiValueMap<>();

	private final Map<FieldCoordinates, DataFetcher<?>> unmappedDataFetchers = new LinkedHashMap<>();

	private final Set<String> skippedTypes = new LinkedHashSet<>();

	@Nullable
	private SchemaMappingReport report;


	private SchemaMappingInspector(GraphQLSchema schema, RuntimeWiring runtimeWiring) {
		Assert.notNull(schema, "GraphQLSchema is required");
		Assert.notNull(runtimeWiring, "RuntimeWiring is required");
		this.schema = schema;
		this.runtimeWiring = runtimeWiring;
	}


	/**
	 * Perform an inspection and create a {@link SchemaMappingReport}.
	 * The inspection is one once only, during the first call to this method.
	 */
	public SchemaMappingReport getOrCreateReport() {
		if (this.report == null) {
			checkSchema();
			checkDataFetcherRegistrations();
			this.report = new SchemaMappingReport(
					this.unmappedFields, this.unmappedDataFetchers, this.skippedTypes);
		}
		return this.report;
	}

	private void checkSchema() {

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
	 * @param fields the GraphQL interface or object type to check
	 * @param resolvableType the Java type to match against, or {@code null} if
	 * not applicable such as for Query, Mutation, or Subscription
	 */
	@SuppressWarnings("rawtypes")
	private void checkFieldsContainer(GraphQLFieldsContainer fields, @Nullable ResolvableType resolvableType) {

		Map<String, DataFetcher> dataFetcherMap = this.runtimeWiring.getDataFetcherForType(fields.getName());

		for (GraphQLFieldDefinition field : fields.getFieldDefinitions()) {
			String fieldName = field.getName();
			if (dataFetcherMap.containsKey(fieldName)) {
				DataFetcher<?> fetcher = dataFetcherMap.get(fieldName);
				if (fetcher instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher) {
					checkFieldType(
							field.getType(), selfDescribingDataFetcher.getReturnType(),
							(fields == this.schema.getSubscriptionType()));
				}
				else if (isNotScalarOrEnumType(field.getType())) {
					addSkippedType(field.getType(), () ->
							fetcher.getClass().getName() + " does not implement SelfDescribingDataFetcher.");
				}
			}
			else if (resolvableType == null || !hasProperty(resolvableType, fieldName)) {
				this.unmappedFields.add(fields.getName(), fieldName);
			}
		}
	}

	/**
	 * Check the output {@link GraphQLType} of a field against the given DataFetcher return type.
	 * @param outputType the field type to inspect
	 * @param resolvableType the expected Java return type
	 * @param isSubscriptionField whether this is for a subscription field
	 */
	private void checkFieldType(GraphQLType outputType, ResolvableType resolvableType, boolean isSubscriptionField) {

		// Remove GraphQL type wrappers, and nest within Java generic types
		outputType = unwrapIfNonNull(outputType);
		if (isPaginatedType(outputType)) {
			outputType = getPaginatedType((GraphQLObjectType) outputType);
			resolvableType = nestForConnection(resolvableType);
		}
		else if (outputType instanceof GraphQLList listType) {
			outputType = unwrapIfNonNull(listType.getWrappedType());
			resolvableType = nestForList(resolvableType, isSubscriptionField);
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
				String schemaTypeName = outputType.getClass().getSimpleName();
				addSkippedType(outputType, () -> "inspection does not support " + schemaTypeName + ".");
			}
			return;
		}

		// Can we inspect Java type?
		if (resolvableType.resolve(Object.class) == Object.class) {
			addSkippedType(outputType, () -> "inspection could not determine the Java object return type.");
			return;
		}

		// Nest within the
		checkFieldsContainer(fieldContainer, resolvableType);
	}

	private GraphQLType unwrapIfNonNull(GraphQLType type) {
		return (type instanceof GraphQLNonNull graphQLNonNull ? graphQLNonNull.getWrappedType() : type);
	}

	private boolean isPaginatedType(GraphQLType type) {
		return (type instanceof GraphQLObjectType objectType &&
				objectType.getName().endsWith("Connection") &&
				objectType.getField("edges") != null && objectType.getField("pageInfo") != null);
	}

	private GraphQLType getPaginatedType(GraphQLObjectType type) {
		String name = type.getName().substring(0, type.getName().length() - 10);
		GraphQLType nodeType = this.schema.getType(name);
		Assert.state(nodeType != null, "No node type for '" + type.getName() + "'");
		return nodeType;
	}

	private ResolvableType nestForConnection(ResolvableType type) {
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
		if (logger.isDebugEnabled() && (!type.isArray() && type.getGenerics().length != 1)) {
			logger.debug("Expected List compatible type: " + type);
		}
		return type.getNested(2);
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

	private void addSkippedType(GraphQLType type, Supplier<String> reason) {
		String typeName = typeNameToString(type);
		this.skippedTypes.add(typeName);
		if (logger.isDebugEnabled()) {
			logger.debug("Skipped '" + typeName + "': " + reason.get());
		}
	}

	@SuppressWarnings("rawtypes")
	private void checkDataFetcherRegistrations() {
		this.runtimeWiring.getDataFetchers().forEach((typeName, registrations) ->
				registrations.forEach((fieldName, fetcher) -> {
					FieldCoordinates coordinates = FieldCoordinates.coordinates(typeName, fieldName);
					if (this.schema.getFieldDefinition(coordinates) == null) {
						this.unmappedDataFetchers.put(coordinates, fetcher);
					}
				}));
	}


	/**
	 * Check the schema against {@code DataFetcher} registrations, and produce a report.
	 * @param schema the schema to inspect
	 * @param runtimeWiring for {@code DataFetcher} registrations
	 * @return the created report
	 */
	public static SchemaMappingReport inspect(GraphQLSchema schema, RuntimeWiring runtimeWiring) {
		return new SchemaMappingInspector(schema, runtimeWiring).getOrCreateReport();
	}


}
