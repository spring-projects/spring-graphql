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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import graphql.schema.DataFetcher;
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
 * Provide {@link #inspect(GraphQLSchema, RuntimeWiring)} method that checks if
 * schema fields are covered by either a {@link DataFetcher} registration, or a
 * Java object property. Fields that have neither are reported as unmapped in
 * the output {@link Report}.
 *
 * <p>The inspection depends on {@code DataFetcher}s to expose return type
 * information by implementing {@link SelfDescribingDataFetcher}. This allows
 * checking if Java object types have properties that match schema fields.
 * If a {@code DataFetcher} does not implement this interface, then the Java
 * object type is not known, and the field type is reported as skipped.
 *
 * <p>The {@link SelfDescribingDataFetcher} for annotated controller methods
 * exposes the declared return type of the controller method. If the return type
 * is {@link Object} such as for a union, then the Java object structure is
 * not known, and the field output type is reported as skipped.
 *
 * <p>Union types are automatically skipped because there is no way for an
 * annotated controller method to declare the actual Java types.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
class SchemaMappingInspector {

	private final GraphQLSchema schema;

	private final RuntimeWiring runtimeWiring;

	private final ReportBuilder reportBuilder = new ReportBuilder();

	private final Set<String> seenTypes = new HashSet<>();

	private final ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();


	private SchemaMappingInspector(GraphQLSchema schema, RuntimeWiring runtimeWiring) {
		Assert.notNull(schema, "GraphQLSchema is required");
		Assert.notNull(runtimeWiring, "RuntimeWiring is required");
		this.schema = schema;
		this.runtimeWiring = runtimeWiring;
	}


	/**
	 * Inspect all fields, starting from Query, Mutation, and Subscription, and
	 * working recursively down through the types they return.
	 * @return a report with unmapped fields and skipped types.
	 */
	public Report inspect() {

		inspectType(this.schema.getQueryType(), null);

		if (this.schema.isSupportingMutations()) {
			inspectType(this.schema.getMutationType(), null);
		}

		if (this.schema.isSupportingSubscriptions()) {
			inspectType(this.schema.getSubscriptionType(), null);
		}

		return this.reportBuilder.build();
	}

	@SuppressWarnings("rawtypes")
	private void inspectType(GraphQLType type, @Nullable ResolvableType resolvableType) {
		Assert.notNull(type, "No GraphQLType");

		type = unwrapNonNull(type);
		if (isConnectionType(type)) {
			type = getConnectionNodeType(type);
			resolvableType = nest(resolvableType, type);
		}
		else if (type instanceof GraphQLList listType) {
			type = unwrapNonNull(listType.getWrappedType());
			resolvableType = nest(resolvableType, type);
		}

		if (type instanceof GraphQLNamedOutputType outputType) {
			if (!this.seenTypes.add(outputType.getName())) {
				return;
			}
		}

		if (!(type instanceof GraphQLFieldsContainer fieldContainer)) {
			if (isNotScalarOrEnumType(type)) {
				this.reportBuilder.addSkippedType(getTypeName(type));
			}
			return;
		}
		else if (resolvableType != null && resolveClassToCompare(resolvableType) == Object.class) {
			this.reportBuilder.addSkippedType(getTypeName(type));
			return;
		}

		String typeName = fieldContainer.getName();
		Map<String, DataFetcher> dataFetcherMap = this.runtimeWiring.getDataFetcherForType(typeName);

		for (GraphQLFieldDefinition field : fieldContainer.getFieldDefinitions()) {
			String fieldName = field.getName();
			if (dataFetcherMap.containsKey(fieldName)) {
				DataFetcher fetcher = dataFetcherMap.get(fieldName);
				if (fetcher instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher) {
					inspectType(field.getType(), selfDescribingDataFetcher.getReturnType());
				}
				else if (isNotScalarOrEnumType(field.getType())) {
					this.reportBuilder.addSkippedType(getTypeName(field.getType()));
				}
			}
			else if (resolvableType == null || !hasProperty(resolvableType, fieldName)) {
				this.reportBuilder.addUnmappedField(typeName, fieldName);
			}
		}
	}

	private GraphQLType unwrapNonNull(GraphQLType type) {
		return (type instanceof GraphQLNonNull graphQLNonNull ? graphQLNonNull.getWrappedType() : type);
	}

	private boolean isConnectionType(GraphQLType type) {
		return (type instanceof GraphQLObjectType objectType &&
				objectType.getName().endsWith("Connection") &&
				objectType.getField("edges") != null && objectType.getField("pageInfo") != null);
	}

	private GraphQLType getConnectionNodeType(GraphQLType type) {
		String name = ((GraphQLObjectType) type).getName();
		name = name.substring(0, name.length() - 10);
		type = this.schema.getType(name);
		Assert.state(type != null, "No '" + name + "' type");
		return type;
	}

	private static ResolvableType nest(@Nullable ResolvableType resolvableType, GraphQLType type) {
		Assert.notNull(resolvableType, "No declaredType for " + getTypeName(type));
		resolvableType = resolvableType.getNested(2);
		return resolvableType;
	}

	private static String getTypeName(GraphQLType type) {
		return (type instanceof GraphQLNamedType namedType ? namedType.getName() : type.toString());
	}

	private static boolean isNotScalarOrEnumType(GraphQLType type) {
		return !(type instanceof GraphQLScalarType || type instanceof GraphQLEnumType);
	}

	private boolean hasProperty(ResolvableType resolvableType, String fieldName) {
		try {
			Class<?> clazz = resolveClassToCompare(resolvableType);
			return (BeanUtils.getPropertyDescriptor(clazz, fieldName) != null);
		}
		catch (BeansException ex) {
			throw new IllegalStateException(
					"Failed to introspect " + resolvableType + " for field '" + fieldName + "'", ex);
		}
	}

	private Class<?> resolveClassToCompare(ResolvableType resolvableType) {
		Class<?> clazz = resolvableType.resolve(Object.class);
		ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(clazz);
		return (adapter != null ? resolvableType.getNested(2).resolve(Object.class) : clazz);
	}


	/**
	 * Check the schema against {@code DataFetcher} registrations, and produce a report.
	 * @param schema the schema to inspect
	 * @param runtimeWiring for {@code DataFetcher} registrations
	 * @return the created report
	 */
	public static Report inspect(GraphQLSchema schema, RuntimeWiring runtimeWiring) {
		SchemaMappingInspector inspector = new SchemaMappingInspector(schema, runtimeWiring);
		return inspector.inspect();
	}



	/**
	 * Container of unmapped fields and skipped types.
	 * @param unmappedFields fields with neither {@link DataFetcher} mapping nor Object property
	 * @param skippedTypes types that could not be verified, e.g. union
	 */
	public record Report(MultiValueMap<String, String> unmappedFields, Set<String> skippedTypes) {

		@Override
		public String toString() {
			return "GraphQL schema inspection:\n" +
					"\tUnmapped fields: " + this.unmappedFields + "\n" +
					"\tSkipped types: " + this.skippedTypes;
		}
	}


	/**
	 * Builder for a {@link Report}.
	 */
	private static class ReportBuilder {

		private final MultiValueMap<String, String> unmappedFields = new LinkedMultiValueMap<>();

		private final Set<String> skippedTypes = new LinkedHashSet<>();

		/**
		 * Add an unmapped field.
		 */
		public void addUnmappedField(String typeName, String fieldName) {
			this.unmappedFields.add(typeName, fieldName);
		}

		/**
		 * Add a skipped type name.
		 */
		public void addSkippedType(String typeName) {
			this.skippedTypes.add(typeName);
		}

		public Report build() {
			return new Report(
					new LinkedMultiValueMap<>(this.unmappedFields),
					new LinkedHashSet<>(this.skippedTypes));
		}

	}

}
