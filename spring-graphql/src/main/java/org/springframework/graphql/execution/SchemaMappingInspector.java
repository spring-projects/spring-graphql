/*
 * Copyright 2020-present the original author or authors.
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

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import graphql.language.FieldDefinition;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.idl.RuntimeWiring;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.core.MethodParameter;
import org.springframework.core.Nullness;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Inspect schema mappings on startup to ensure the following:
 * <ul>
 * <li>Schema fields have either a {@link DataFetcher} registration or a
 * corresponding Class property.
 * <li>{@code DataFetcher} registrations refer to a schema field that exists.
 * <li>{@code DataFetcher} arguments have matching schema field arguments.
 * <li>The nullness of {@link DataFetcher} return types, class properties or class methods
 * match, or is more restrictive than, the nullness of schema fields.
 * <li>The nullness of {@code DataFetcher} arguments match, or is more restrictive than,
 * the nullness of schema argument types.
 * </ul>
 *
 * <p>Use methods of {@link GraphQlSource.SchemaResourceBuilder} to enable schema
 * inspection on startup. For all other cases, use {@link #initializer()} as a
 * starting point or the shortcut {@link #inspect(GraphQLSchema, Map)}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
@SuppressWarnings("rawtypes")
public final class SchemaMappingInspector {

	private static final Log logger = LogFactory.getLog(SchemaMappingInspector.class);

	/**
	 * GraphQL Java detects "record-like" methods that match field names.
	 * This predicate aims to match the method {@code isRecordLike(Method)} in
	 * {@link graphql.schema.fetching.LambdaFetchingSupport}.
	 */
	private static final Predicate<Method> recordLikePredicate = (method) ->
			(!method.getDeclaringClass().equals(Object.class) && !method.getReturnType().equals(Void.class) &&
					method.getParameterCount() == 0 && Modifier.isPublic(method.getModifiers()));


	private final GraphQLSchema schema;

	private final Map<String, Map<String, DataFetcher>> dataFetchers;

	private final InterfaceUnionLookup interfaceUnionLookup;

	private final Set<String> inspectedTypes = new HashSet<>();

	private final ReportBuilder reportBuilder = new ReportBuilder();

	private @Nullable SchemaReport report;


	private SchemaMappingInspector(
			GraphQLSchema schema, Map<String, Map<String, DataFetcher>> dataFetchers,
			InterfaceUnionLookup interfaceUnionLookup) {

		Assert.notNull(schema, "GraphQLSchema is required");
		Assert.notNull(dataFetchers, "DataFetcher map is required");
		Assert.notNull(interfaceUnionLookup, "InterfaceUnionLookup is required");

		this.schema = schema;
		this.dataFetchers = dataFetchers;
		this.interfaceUnionLookup = interfaceUnionLookup;
	}


	/**
	 * Perform an inspection and create a {@link SchemaReport}.
	 * The inspection is done once only, during the first call to this method.
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

		if (!this.inspectedTypes.add(fieldContainer.getName())) {
			return;
		}

		String typeName = fieldContainer.getName();
		Map<String, DataFetcher> dataFetcherMap = this.dataFetchers.getOrDefault(typeName, Collections.emptyMap());

		for (GraphQLFieldDefinition field : fieldContainer.getFieldDefinitions()) {
			String fieldName = field.getName();
			DataFetcher<?> dataFetcher = dataFetcherMap.get(fieldName);
			FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates(typeName, fieldName);
			Nullness schemaNullness = resolveNullness(field);

			if (dataFetcher != null) {
				if (dataFetcher instanceof SelfDescribingDataFetcher<?> selfDescribing) {
					checkDataFetcherNullness(fieldCoordinates, selfDescribing, schemaNullness);
					checkFieldArguments(field, selfDescribing);
					checkFieldArgumentsNullness(field, selfDescribing);
					checkField(fieldContainer, field, selfDescribing.getReturnType());
				}
				else {
					checkField(fieldContainer, field, ResolvableType.NONE);
				}
				continue;
			}

			if (resolvableType != null) {
				PropertyDescriptor descriptor = getProperty(resolvableType, fieldName);
				if (descriptor != null) {
					MethodParameter returnType = new MethodParameter(descriptor.getReadMethod(), -1);
					checkReadMethodNullness(fieldCoordinates, resolvableType, descriptor, schemaNullness);
					checkField(fieldContainer, field, ResolvableType.forMethodParameter(returnType, resolvableType));
					continue;
				}
				Field javaField = getField(resolvableType, fieldName);
				if (javaField != null) {
					checkFieldNullNess(fieldCoordinates, javaField, schemaNullness);
					checkField(fieldContainer, field, ResolvableType.forField(javaField));
					continue;
				}
				// Kotlin function, Boolean is<PropertyName>
				Method method = getOtherAccessor(resolvableType, fieldName);
				if (method != null) {
					MethodParameter returnType = new MethodParameter(method, -1);
					checkField(fieldContainer, field, ResolvableType.forMethodParameter(returnType, resolvableType));
					continue;
				}
			}

			this.reportBuilder.unmappedField(fieldCoordinates);
		}
	}

	private void checkFieldNullNess(FieldCoordinates fieldCoordinates, Field javaField, Nullness schemaNullness) {
		Nullness applicationNullness = Nullness.forField(javaField);
		if (isNullnessError(schemaNullness, applicationNullness)) {
			DescribedAnnotatedElement annotatedElement = new DescribedAnnotatedElement(javaField,
					javaField.getDeclaringClass().getSimpleName() + "#" + javaField.getName());
			this.reportBuilder.fieldNullnessError(fieldCoordinates,
					new DefaultNullnessError(schemaNullness, applicationNullness, annotatedElement));

		}
	}

	private void checkDataFetcherNullness(FieldCoordinates fieldCoordinates, SelfDescribingDataFetcher dataFetcher, Nullness schemaNullness) {
		Method dataFetcherMethod = dataFetcher.asMethod();
		if (dataFetcherMethod != null) {
			Nullness applicationNullness = Nullness.forMethodReturnType(dataFetcherMethod);
			ReactiveAdapter reactiveAdapter = ReactiveAdapterRegistry.getSharedInstance()
					.getAdapter(dataFetcherMethod.getReturnType());
			if (reactiveAdapter != null) {
				// we cannot infer nullness if wrapped by reactive types
				logger.debug("Skip nullness check for data fetcher '" + dataFetcherMethod.getName() + "' because of Reactive return type.");
			}
			else if (dataFetcher.usesDataLoader() && Map.class.isAssignableFrom(dataFetcherMethod.getReturnType())) {
				// we cannot infer nullness if batch loader method returns a Map
				logger.debug("Skip nullness check for data fetcher '" + dataFetcherMethod.getName() + "' because of batch loading.");
			}
			else if (isNullnessError(schemaNullness, applicationNullness)) {
				DescribedAnnotatedElement annotatedElement = new DescribedAnnotatedElement(dataFetcherMethod, dataFetcher.getDescription());
				this.reportBuilder.fieldNullnessError(fieldCoordinates,
						new DefaultNullnessError(schemaNullness, applicationNullness, annotatedElement));

			}
		}
	}

	private void checkFieldArguments(GraphQLFieldDefinition field, SelfDescribingDataFetcher<?> dataFetcher) {
		List<String> arguments = new ArrayList<>();
		for (String name : dataFetcher.getArguments().keySet()) {
			if (field.getArgument(name) == null) {
				arguments.add(name);
			}
		}
		if (!arguments.isEmpty()) {
			this.reportBuilder.unmappedArgument(dataFetcher, arguments);
		}
	}

	private void checkFieldArgumentsNullness(GraphQLFieldDefinition field, SelfDescribingDataFetcher<?> dataFetcher) {
		Method dataFetcherMethod = dataFetcher.asMethod();
		if (dataFetcherMethod != null) {
			List<SchemaReport.NullnessError> nullnessErrors = new ArrayList<>();
			for (Parameter parameter : dataFetcherMethod.getParameters()) {
				GraphQLArgument argument = field.getArgument(parameter.getName());
				if (argument != null && argument.getDefinition() != null) {
					Nullness schemaNullness = resolveNullness(argument.getDefinition().getType());
					Nullness applicationNullness = Nullness.forMethodParameter(MethodParameter.forParameter(parameter));
					if (isNullnessError(schemaNullness, applicationNullness)) {
						nullnessErrors.add(new DefaultNullnessError(schemaNullness, applicationNullness, parameter));
					}
				}
			}
			if (!nullnessErrors.isEmpty()) {
				this.reportBuilder.argumentsNullnessErrors(dataFetcher, nullnessErrors);
			}
		}
	}

	private void checkReadMethodNullness(FieldCoordinates fieldCoordinates, ResolvableType resolvableType, PropertyDescriptor descriptor, Nullness schemaNullness) {
		Nullness applicationNullness = Nullness.forMethodReturnType(descriptor.getReadMethod());
		if (isNullnessError(schemaNullness, applicationNullness)) {
			DescribedAnnotatedElement annotatedElement = new DescribedAnnotatedElement(descriptor.getReadMethod(),
					resolvableType.toClass().getSimpleName() + "#" + descriptor.getName());
			this.reportBuilder.fieldNullnessError(fieldCoordinates,
					new DefaultNullnessError(schemaNullness, applicationNullness, annotatedElement));
		}
	}


	/**
	 * Resolve field wrapper types (connection, list, non-null), nest into generic types,
	 * and recurse with {@link #checkFieldsContainer} if there is enough type information.
	 */
	private void checkField(
			GraphQLFieldsContainer parent, GraphQLFieldDefinition field, ResolvableType resolvableType) {

		TypePair typePair = TypePair.resolveTypePair(parent, field, resolvableType, this.schema);

		MultiValueMap<GraphQLType, ResolvableType> typePairs = new LinkedMultiValueMap<>();
		if (typePair.outputType() instanceof GraphQLUnionType unionType) {
			typePairs.putAll(this.interfaceUnionLookup.resolveUnion(unionType));
		}
		else if (typePair.outputType() instanceof GraphQLInterfaceType interfaceType) {
			typePairs.putAll(this.interfaceUnionLookup.resolveInterface(interfaceType));
		}

		if (typePairs.isEmpty()) {
			typePairs.add(typePair.outputType(), typePair.resolvableType());
		}

		for (Map.Entry<GraphQLType, List<ResolvableType>> entry : typePairs.entrySet()) {
			GraphQLType graphQlType = entry.getKey();

			for (ResolvableType currentResolvableType : entry.getValue()) {

				// Can we inspect GraphQL type?
				if (!(graphQlType instanceof GraphQLFieldsContainer fieldContainer)) {
					if (isNotScalarOrEnumType(graphQlType)) {
						this.reportBuilder.skippedType(graphQlType, parent, field, "Unsupported schema type", false);
					}
					continue;
				}

				// Can we inspect the Class?
				if (currentResolvableType.resolve(Object.class) == Object.class) {
					boolean isDerived = !graphQlType.equals(typePair.outputType());
					this.reportBuilder.skippedType(graphQlType, parent, field, "No class information", isDerived);
					continue;
				}

				checkFieldsContainer(fieldContainer, currentResolvableType);
			}
		}
	}

	private @Nullable PropertyDescriptor getProperty(ResolvableType resolvableType, String fieldName) {
		try {
			Class<?> clazz = resolvableType.resolve();
			return (clazz != null) ? BeanUtils.getPropertyDescriptor(clazz, fieldName) : null;
		}
		catch (BeansException ex) {
			throw new IllegalStateException(
					"Failed to get property on " + resolvableType + " for field '" + fieldName + "'", ex);
		}
	}

	private @Nullable Field getField(ResolvableType resolvableType, String fieldName) {
		try {
			Class<?> clazz = resolvableType.resolve();
			return (clazz != null) ? clazz.getField(fieldName) : null;
		}
		catch (NoSuchFieldException ex) {
			return null;
		}
	}

	private static @Nullable Method getOtherAccessor(ResolvableType resolvableType, String fieldName) {
		Class<?> clazz = resolvableType.resolve();
		if (clazz != null) {
			for (Method method : clazz.getDeclaredMethods()) {
				if (recordLikePredicate.test(method) && fieldName.equals(StringUtils.uncapitalize(method.getName()))) {
					return method;
				}
				// JavaBean introspection supports boolean property only
				if (method.getReturnType().equals(Boolean.class) &&
						method.getName().equals("is" + StringUtils.capitalize(fieldName))) {
					return method;
				}
			}
		}
		return null;
	}

	private static boolean isNotScalarOrEnumType(GraphQLType type) {
		return !(type instanceof GraphQLScalarType || type instanceof GraphQLEnumType);
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

	private Nullness resolveNullness(GraphQLFieldDefinition fieldDefinition) {
		FieldDefinition definition = fieldDefinition.getDefinition();
		if (definition != null) {
			return resolveNullness(definition.getType());
		}
		return Nullness.UNSPECIFIED;
	}

	private Nullness resolveNullness(Type type) {
		if (type instanceof NonNullType) {
			return Nullness.NON_NULL;
		}
		return Nullness.NULLABLE;
	}

	private boolean isNullnessError(Nullness schemaNullness, Nullness applicationNullness) {
		return (schemaNullness == Nullness.NON_NULL && applicationNullness == Nullness.NULLABLE);
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
	 * @param schema the schema to inspect
	 * @param fetchers the map of {@code DataFetcher} registrations
	 * @since 1.2.5
	 */
	public static SchemaReport inspect(GraphQLSchema schema, Map<String, Map<String, DataFetcher>> fetchers) {
		return initializer().inspect(schema, fetchers);
	}

	/**
	 * Return an initializer to configure the {@link SchemaMappingInspector}
	 * and perform the inspection.
	 * @since 1.3.0
	 */
	public static Initializer initializer() {
		return new DefaultInitializer();
	}


	/**
	 * Helps to configure {@link SchemaMappingInspector}.
	 * @since 1.3.0
	 */
	public interface Initializer {

		/**
		 * Provide an explicit mapping between a GraphQL type name and the Java
		 * class(es) that represent it at runtime to help inspect union member
		 * and interface implementation types when those associations cannot be
		 * discovered otherwise.
		 * <p>Out of the box, there a several ways through which schema inspection
		 * can locate such types automatically:
		 * <ul>
		 * <li>Java class representations are located in the same package as the
		 * type returned from the controller method for a union or interface field,
		 * and their {@link Class#getSimpleName() simple class names} match GraphQL
		 * type names, possibly with the help of a {@link #classNameFunction}.
		 * <li>Java class representations are located in the same package as the
		 * declaring class of the controller method for a union or interface field.
		 * <li>Controller methods return the Java class representations of schema
		 * fields for concrete union member or interface implementation types.
		 * </ul>
		 * @param graphQlTypeName the name of a GraphQL Object type
		 * @param aClass one or more Java class representations
		 * @return the same initializer instance
		 */
		Initializer classMapping(String graphQlTypeName, Class<?>... aClass);

		/**
		 * Help to derive the {@link Class#getSimpleName() simple class name} for
		 * the Java representation of a GraphQL union member or interface implementing
		 * type. For more details, see {@link #classMapping(String, Class[])}.
		 * <p>By default, {@link GraphQLObjectType#getName()} is used.
		 * @param function the function to use
		 * @return the same initializer instance
		 */
		Initializer classNameFunction(Function<GraphQLObjectType, String> function);

		/**
		 * Alternative to {@link #classMapping(String, Class[])} with a custom
		 * {@link ClassResolver} to find the Java class(es) for a GraphQL union
		 * member or interface implementation type.
		 * @param resolver the resolver to use to find associated Java classes
		 * @return the same initializer instance
		 */
		Initializer classResolver(ClassResolver resolver);

		/**
		 * Perform the inspection and return a report.
		 * @param schema the schema to inspect
		 * @param fetchers the registered data fetchers
		 * @return the produced report
		 */
		SchemaReport inspect(GraphQLSchema schema, Map<String, Map<String, DataFetcher>> fetchers);

	}


	/**
	 * Strategy to resolve the Java class(es) for a {@code GraphQLObjectType}, effectively
	 * the reverse of {@link graphql.schema.TypeResolver}, for schema inspection purposes.
	 */
	public interface ClassResolver {

		/**
		 * Return Java class(es) for the given GraphQL object type.
		 * @param objectType the {@code GraphQLObjectType} to resolve
		 * @param interfaceOrUnionType either an interface the object implements,
		 * or a union the object is a member of
		 */
		List<Class<?>> resolveClass(GraphQLObjectType objectType, GraphQLNamedOutputType interfaceOrUnionType);

	}


	/**
	 * Default implementation of {@link Initializer}.
	 */
	private static final class DefaultInitializer implements Initializer {

		private Function<GraphQLObjectType, String> classNameFunction = GraphQLObjectType::getName;

		private final List<ClassResolver> classResolvers = new ArrayList<>();

		private final MultiValueMap<String, Class<?>> classMappings = new LinkedMultiValueMap<>();

		@Override
		public Initializer classNameFunction(Function<GraphQLObjectType, String> function) {
			this.classNameFunction = function;
			return this;
		}

		@Override
		public Initializer classResolver(ClassResolver resolver) {
			this.classResolvers.add(resolver);
			return this;
		}

		@Override
		public Initializer classMapping(String graphQlTypeName, Class<?>... classes) {
			for (Class<?> aClass : classes) {
				this.classMappings.add(graphQlTypeName, aClass);
			}
			return this;
		}

		@Override
		public SchemaReport inspect(GraphQLSchema schema, Map<String, Map<String, DataFetcher>> fetchers) {

			List<ClassResolver> resolvers = new ArrayList<>(this.classResolvers);
			resolvers.add(new MappingClassResolver(this.classMappings));
			resolvers.add(ReflectionClassResolver.create(schema, fetchers, this.classNameFunction));

			InterfaceUnionLookup lookup = InterfaceUnionLookup.create(schema, resolvers);

			SchemaMappingInspector inspector = new SchemaMappingInspector(schema, fetchers, lookup);
			return inspector.getOrCreateReport();
		}
	}


	/**
	 * ClassResolver with explicit mappings.
	 */
	private static final class MappingClassResolver implements ClassResolver {

		private final MultiValueMap<String, Class<?>> mappings = new LinkedMultiValueMap<>();

		MappingClassResolver(MultiValueMap<String, Class<?>> mappings) {
			this.mappings.putAll(mappings);
		}

		@Override
		public List<Class<?>> resolveClass(GraphQLObjectType objectType, GraphQLNamedOutputType interfaceOrUnionType) {
			return this.mappings.getOrDefault(objectType.getName(), Collections.emptyList());
		}
	}


	/**
	 * ClassResolver that uses a function to derive the simple class name from
	 * the GraphQL object type, and then prepends a prefixes such as a package
	 * name and/or an outer class name.
	 */
	private static final class ReflectionClassResolver implements ClassResolver {

		private static final Predicate<String> PACKAGE_PREDICATE = (name) -> !name.startsWith("java.");

		private final Function<GraphQLObjectType, String> classNameFunction;

		private final MultiValueMap<String, String> classPrefixes;

		private ReflectionClassResolver(
				Function<GraphQLObjectType, String> nameFunction, MultiValueMap<String, String> prefixes) {

			this.classNameFunction = nameFunction;
			this.classPrefixes = prefixes;
		}

		@Override
		public List<Class<?>> resolveClass(GraphQLObjectType objectType, GraphQLNamedOutputType interfaceOrUnion) {
			String className = this.classNameFunction.apply(objectType);
			for (String prefix : this.classPrefixes.getOrDefault(interfaceOrUnion.getName(), Collections.emptyList())) {
				try {
					Class<?> clazz = Class.forName(prefix + className);
					return Collections.singletonList(clazz);
				}
				catch (ClassNotFoundException ex) {
					// Ignore
				}
			}
			return Collections.emptyList();
		}

		/**
		 * Create a resolver that is aware of packages associated with controller
		 * methods mapped to unions and interfaces.
		 */
		public static ReflectionClassResolver create(
				GraphQLSchema schema, Map<String, Map<String, DataFetcher>> dataFetchers,
				Function<GraphQLObjectType, String> classNameFunction) {

			MultiValueMap<String, String> classPrefixes = new LinkedMultiValueMap<>();

			for (Map.Entry<String, Map<String, DataFetcher>> typeEntry : dataFetchers.entrySet()) {
				String typeName = typeEntry.getKey();
				GraphQLType parentType = schema.getType(typeName);
				if (parentType == null) {
					continue;  // Unmapped registration
				}
				for (Map.Entry<String, DataFetcher> fieldEntry : typeEntry.getValue().entrySet()) {
					FieldCoordinates coordinates = FieldCoordinates.coordinates(typeName, fieldEntry.getKey());
					GraphQLFieldDefinition field = schema.getFieldDefinition(coordinates);
					if (field == null) {
						continue;  // Unmapped registration
					}
					DataFetcher dataFetcher = fieldEntry.getValue();
					TypePair pair = TypePair.resolveTypePair(parentType, field, dataFetcher, schema);
					GraphQLType outputType = pair.outputType();
					if (outputType instanceof GraphQLUnionType || outputType instanceof GraphQLInterfaceType) {
						String outputTypeName = ((GraphQLNamedOutputType) outputType).getName();
						Class<?> clazz = pair.resolvableType().resolve(Object.class);
						if (PACKAGE_PREDICATE.test(clazz.getPackageName())) {
							addClassPrefix(outputTypeName, clazz, classPrefixes);
						}
						if (dataFetcher instanceof SelfDescribingDataFetcher<?> selfDescribing) {
							if (selfDescribing.getReturnType().getSource() instanceof MethodParameter param) {
								addClassPrefix(outputTypeName, param.getDeclaringClass(), classPrefixes);
							}
						}
					}
				}
			}

			return new ReflectionClassResolver(classNameFunction, classPrefixes);
		}

		private static void addClassPrefix(
				String unionOrInterfaceType, Class<?> aClass, MultiValueMap<String, String> classPrefixes) {

			int index = aClass.getName().indexOf(aClass.getSimpleName());
			classPrefixes.add(unionOrInterfaceType, aClass.getName().substring(0, index));
		}
	}


	/**
	 * Lookup for GraphQL Object and Java type pairs that are associated with
	 * GraphQL union and interface types.
	 */
	private static final class InterfaceUnionLookup {

		private static final LinkedMultiValueMap<GraphQLType, ResolvableType> EMPTY_MAP = new LinkedMultiValueMap<>(0);

		/** Interface or union type name to implementing or member GraphQL-Java types pairs. */
		private final Map<String, MultiValueMap<GraphQLType, ResolvableType>> mappings;

		private InterfaceUnionLookup(Map<String, MultiValueMap<GraphQLType, ResolvableType>> mappings) {
			this.mappings = mappings;
		}

		/**
		 * Resolve the implementation GraphQL and Java type pairs for the interface.
		 * @param interfaceType the interface type to resolve type pairs for
		 * @return {@code MultiValueMap} with one or more pairs, possibly one
		 * pair with {@link ResolvableType#NONE}.
		 */
		MultiValueMap<GraphQLType, ResolvableType> resolveInterface(GraphQLInterfaceType interfaceType) {
			return this.mappings.getOrDefault(interfaceType.getName(), EMPTY_MAP);
		}

		/**
		 * Resolve the member GraphQL and Java type pairs for the union.
		 * @param unionType the union type to resolve type pairs for
		 * @return {@code MultiValueMap} with one or more pairs, possibly one
		 * pair with {@link ResolvableType#NONE}.
		 */
		MultiValueMap<GraphQLType, ResolvableType> resolveUnion(GraphQLUnionType unionType) {
			return this.mappings.getOrDefault(unionType.getName(), EMPTY_MAP);
		}

		/**
		 * Resolve the class for every union member and interface implementation type,
		 * and create a lookup instance.
		 */
		public static InterfaceUnionLookup create(
				GraphQLSchema schema, List<ClassResolver> classResolvers) {

			Map<String, MultiValueMap<GraphQLType, ResolvableType>> mappings = new LinkedHashMap<>();

			for (GraphQLNamedType type : schema.getAllTypesAsList()) {
				if (type instanceof GraphQLUnionType union) {
					for (GraphQLNamedOutputType member : union.getTypes()) {
						addTypeMapping(union, (GraphQLObjectType) member, classResolvers, mappings);
					}
				}
				else if (type instanceof GraphQLObjectType objectType) {
					for (GraphQLNamedOutputType interfaceType : objectType.getInterfaces()) {
						addTypeMapping(interfaceType, objectType, classResolvers, mappings);
					}
				}
			}

			return new InterfaceUnionLookup(mappings);
		}

		private static void addTypeMapping(
				GraphQLNamedOutputType interfaceOrUnionType, GraphQLObjectType objectType,
				List<ClassResolver> classResolvers,
				Map<String, MultiValueMap<GraphQLType, ResolvableType>> mappings) {

			List<ResolvableType> resolvableTypes = new ArrayList<>();

			for (ClassResolver resolver : classResolvers) {
				List<Class<?>> classes = resolver.resolveClass(objectType, interfaceOrUnionType);
				if (!classes.isEmpty()) {
					for (Class<?> clazz : classes) {
						ResolvableType resolvableType = ResolvableType.forClass(clazz);
						resolvableTypes.add(resolvableType);
					}
					break;
				}
			}

			if (resolvableTypes.isEmpty()) {
				resolvableTypes.add(ResolvableType.NONE);
			}

			for (ResolvableType resolvableType : resolvableTypes) {
				String name = interfaceOrUnionType.getName();
				mappings.computeIfAbsent(name, (n) -> new LinkedMultiValueMap<>()).add(objectType, resolvableType);
			}
		}
	}


	/**
	 * Container for a GraphQL and Java type pair along with logic to resolve the
	 * pair of types for a GraphQL field and the {@code DataFetcher} registered for it.
	 */
	private record TypePair(GraphQLType outputType, ResolvableType resolvableType) {

		private static final ReactiveAdapterRegistry adapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

		/**
		 * Convenience variant of
		 * {@link #resolveTypePair(GraphQLType, GraphQLFieldDefinition, ResolvableType, GraphQLSchema)}
		 * with a {@link DataFetcher} to extract the return type from.
		 * @param parent the parent type of the field
		 * @param field the field
		 * @param fetcher the data fetcher associated with this field
		 * @param schema the GraphQL schema
		 */
		public static TypePair resolveTypePair(
				GraphQLType parent, GraphQLFieldDefinition field, DataFetcher<?> fetcher, GraphQLSchema schema) {

			return resolveTypePair(parent, field,
					(fetcher instanceof SelfDescribingDataFetcher<?> sd) ? sd.getReturnType() : ResolvableType.NONE,
					schema);
		}

		/**
		 * Given a GraphQL field and its associated Java type, determine
		 * the type pair to use for schema inspection, removing list, non-null, and
		 * connection type wrappers, and nesting within generic types in order to get
		 * to the types to use for schema inspection.
		 * @param parent the parent type of the field
		 * @param field the field
		 * @param resolvableType the Java type associated with the field
		 * @param schema the GraphQL schema
		 * @return the GraphQL type and corresponding Java type, or {@link ResolvableType#NONE} if unresolved.
		 */
		public static TypePair resolveTypePair(
				GraphQLType parent, GraphQLFieldDefinition field, ResolvableType resolvableType, GraphQLSchema schema) {

			// Remove GraphQL type wrappers, and nest within Java generic types
			GraphQLType outputType = unwrapIfNonNull(field.getType());
			GraphQLType paginatedType = getPaginatedType(outputType);
			if (paginatedType != null) {
				outputType = paginatedType;
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
			return (type instanceof GraphQLNonNull graphQLNonNull) ? graphQLNonNull.getWrappedType() : type;
		}

		private static @Nullable GraphQLType getPaginatedType(GraphQLType type) {
			if (!(type instanceof GraphQLObjectType cot && cot.getName().endsWith("Connection"))) {
				return null;
			}
			GraphQLFieldDefinition edges = cot.getField("edges");
			if (edges == null) {
				return null;
			}
			if (!(unwrapIfNonNull(edges.getType()) instanceof GraphQLList lt)) {
				return null;
			}
			if (!(lt.getWrappedType() instanceof GraphQLObjectType eot)) {
				return null;
			}
			GraphQLFieldDefinition node = eot.getField("node");
			if (node == null) {
				return null;
			}
			return unwrapIfNonNull(node.getType());
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
	private final class ReportBuilder {

		private final List<FieldCoordinates> unmappedFields = new ArrayList<>();

		private final Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations = new LinkedHashMap<>();

		private final MultiValueMap<DataFetcher<?>, String> unmappedArguments = new LinkedMultiValueMap<>();

		private final Map<FieldCoordinates, SchemaReport.NullnessError> fieldNullnessErrors = new LinkedHashMap<>();

		private final MultiValueMap<DataFetcher<?>, SchemaReport.NullnessError> argumentsNullnessErrors = new LinkedMultiValueMap<>();

		private final List<DefaultSkippedType> skippedTypes = new ArrayList<>();

		private final List<DefaultSkippedType> candidateSkippedTypes = new ArrayList<>();

		void unmappedField(FieldCoordinates coordinates) {
			this.unmappedFields.add(coordinates);
		}

		void unmappedRegistration(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
			this.unmappedRegistrations.put(coordinates, dataFetcher);
		}

		void unmappedArgument(DataFetcher<?> dataFetcher, List<String> arguments) {
			this.unmappedArguments.put(dataFetcher, arguments);
		}

		void fieldNullnessError(FieldCoordinates coordinates, SchemaReport.NullnessError nullnessError) {
			this.fieldNullnessErrors.put(coordinates, nullnessError);
		}

		void argumentsNullnessErrors(DataFetcher<?> dataFetcher, List<SchemaReport.NullnessError> nullnessErrors) {
			this.argumentsNullnessErrors.put(dataFetcher, nullnessErrors);
		}

		void skippedType(
				GraphQLType type, GraphQLFieldsContainer parent, GraphQLFieldDefinition field,
				String reason, boolean isDerivedType) {

			DefaultSkippedType skippedType = DefaultSkippedType.create(type, parent, field, reason);

			if (!isDerivedType) {
				skippedType(skippedType);
				return;
			}

			// Keep skipped union member or interface implementing types aside to the end.
			// Use of concrete types elsewhere may provide more information.

			this.candidateSkippedTypes.add(skippedType);
		}

		private void skippedType(DefaultSkippedType skippedType) {
			if (logger.isDebugEnabled()) {
				logger.debug("Skipping '" + skippedType + "': " + skippedType.reason());
			}
			this.skippedTypes.add(skippedType);
		}

		SchemaReport build() {

			this.candidateSkippedTypes.forEach((skippedType) -> {
				if (skippedType.type() instanceof GraphQLFieldsContainer fieldsContainer) {
					if (SchemaMappingInspector.this.inspectedTypes.contains(fieldsContainer.getName())) {
						return;
					}
				}
				skippedType(skippedType);
			});

			return new DefaultSchemaReport(this.unmappedFields, this.unmappedRegistrations,
					this.unmappedArguments, this.fieldNullnessErrors, this.argumentsNullnessErrors, this.skippedTypes);
		}
	}


	/**
	 * Default implementation of {@link SchemaReport}.
	 */
	private final class DefaultSchemaReport implements SchemaReport {

		private final List<FieldCoordinates> unmappedFields;

		private final Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations;

		private final MultiValueMap<DataFetcher<?>, String> unmappedArguments;

		private final Map<FieldCoordinates, NullnessError> fieldNullnessErrors;

		private final MultiValueMap<DataFetcher<?>, NullnessError> argumentNullnessErrors;

		private final List<SchemaReport.SkippedType> skippedTypes;

		DefaultSchemaReport(
				List<FieldCoordinates> unmappedFields, Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations,
				MultiValueMap<DataFetcher<?>, String> unmappedArguments,
				Map<FieldCoordinates, NullnessError> fieldNullnessErrors,
				MultiValueMap<DataFetcher<?>, NullnessError> argumentNullnessErrors,
				List<DefaultSkippedType> skippedTypes) {

			this.unmappedFields = Collections.unmodifiableList(unmappedFields);
			this.unmappedRegistrations = Collections.unmodifiableMap(unmappedRegistrations);
			this.unmappedArguments = CollectionUtils.unmodifiableMultiValueMap(unmappedArguments);
			this.fieldNullnessErrors = Collections.unmodifiableMap(fieldNullnessErrors);
			this.argumentNullnessErrors = CollectionUtils.unmodifiableMultiValueMap(argumentNullnessErrors);
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
		public Map<FieldCoordinates, NullnessError> fieldNullnessErrors() {
			return this.fieldNullnessErrors;
		}

		@Override
		public MultiValueMap<DataFetcher<?>, NullnessError> argumentNullnessErrors() {
			return this.argumentNullnessErrors;
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
		public @Nullable DataFetcher<?> dataFetcher(FieldCoordinates coordinates) {
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
					"\tField nullness errors: " + formatFieldNullnessErrors() + "\n" +
					"\tArgument nullness errors: " + formatArgumentNullnessErrors() + "\n" +
					"\tSkipped types: " + this.skippedTypes;
		}

		private String formatUnmappedFields() {
			MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
			this.unmappedFields.forEach((coordinates) -> {
				List<String> fields = map.computeIfAbsent(coordinates.getTypeName(), (s) -> new ArrayList<>());
				fields.add(coordinates.getFieldName());
			});
			return map.toString();
		}

		private String formatFieldNullnessErrors() {
			MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
			this.fieldNullnessErrors.forEach((coordinates, nullnessError) -> {
				List<String> fields = map.computeIfAbsent(coordinates.getTypeName(), (s) -> new ArrayList<>());
				fields.add(String.format("%s is %s -> '%s' is %s", coordinates.getFieldName(), nullnessError.schemaNullness(),
						nullnessError.annotatedElement(), nullnessError.applicationNullness()));
			});
			return map.toString();
		}

		private String formatArgumentNullnessErrors() {
			MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
			this.argumentNullnessErrors.forEach((dataFetcher, nullnessErrors) -> {
				List<String> arguments = nullnessErrors.stream()
						.map((nullnessError) -> String.format("%s should be %s", nullnessError.annotatedElement(), nullnessError.schemaNullness()))
						.toList();
				map.put(dataFetcher.toString(), arguments);
			});
			return map.toString();
		}

	}


	/**
	 * Default implementation of a {@link SchemaReport.SkippedType}.
	 */
	private record DefaultSkippedType(
			GraphQLType type, FieldCoordinates fieldCoordinates, String reason)
			implements SchemaReport.SkippedType {

		@Override
		public String toString() {
			return (this.type instanceof GraphQLNamedType named) ? named.getName() : this.type.toString();
		}

		public static DefaultSkippedType create(
				GraphQLType type, GraphQLFieldsContainer parent, GraphQLFieldDefinition field, String reason) {

			return new DefaultSkippedType(type, FieldCoordinates.coordinates(parent, field), reason);
		}
	}

	/**
	 * Default implementation of a {@link SchemaReport.NullnessError}.
	 */
	private record DefaultNullnessError(
			Nullness schemaNullness, Nullness applicationNullness, AnnotatedElement annotatedElement)
			implements SchemaReport.NullnessError {

	}

	/**
	 * {@link AnnotatedElement} that overrides the {@code toString} method for displaying in the report.
	 */
	private record DescribedAnnotatedElement(AnnotatedElement delegate,
			String description) implements AnnotatedElement {

		@Override
			public String toString() {
				return this.description;
			}

			@Override
			public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
				return this.delegate.getAnnotation(annotationClass);
			}

			@Override
			public Annotation[] getAnnotations() {
				return this.delegate.getAnnotations();
			}

			@Override
			public Annotation[] getDeclaredAnnotations() {
				return this.delegate.getDeclaredAnnotations();
			}
	}

}
