/*
 * Copyright 2002-2025 the original author or authors.
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

package org.springframework.graphql.data.federation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava.SchemaTransformer;
import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.TypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerDetectionSupport;
import org.springframework.graphql.data.method.annotation.support.AuthenticationPrincipalArgumentResolver;
import org.springframework.graphql.data.method.annotation.support.ContextValueMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.support.ContinuationHandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.support.DataFetchingEnvironmentMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.support.DataLoaderMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.support.LocalContextValueMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.support.PrincipalMethodArgumentResolver;
import org.springframework.graphql.execution.ClassNameTypeResolver;
import org.springframework.graphql.execution.GraphQlSource.SchemaResourceBuilder;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


/**
 * Detects {@link EntityMapping @EntityMapping} handler methods on controllers
 * declared in Spring configuration, and provides factory methods to create
 * {@link GraphQLSchema} or {@link SchemaTransformer}.
 *
 * <p>This class is intended to be declared as a bean in Spring configuration,
 * and plugged in via {@link SchemaResourceBuilder#schemaFactory(BiFunction)}.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 * @see Federation#transform(TypeDefinitionRegistry, RuntimeWiring)
 */
public final class FederationSchemaFactory
		extends AnnotatedControllerDetectionSupport<FederationSchemaFactory.EntityMappingInfo> {

	@Nullable
	private TypeResolver typeResolver;

	private final Map<String, EntityHandlerMethod> handlerMethods = new LinkedHashMap<>();


	/**
	 * Configure a resolver that helps to map Java to entity schema type names.
	 * <p>By default, this is {@link ClassNameTypeResolver}.
	 * @param typeResolver the custom type resolver to use
	 * @see SchemaTransformer#resolveEntityType(TypeResolver)
	 */
	public void setTypeResolver(@Nullable TypeResolver typeResolver) {
		this.typeResolver = typeResolver;
	}


	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();

		detectHandlerMethods().forEach((info) ->
				this.handlerMethods.put(info.typeName(), new EntityHandlerMethod(
						info, getArgumentResolvers(), getExecutor(), shouldInvokeAsync(info.handlerMethod()))));

		if (this.typeResolver == null) {
			this.typeResolver = new ClassNameTypeResolver();
		}

		if (logger.isTraceEnabled()) {
			String formatted = this.handlerMethods.entrySet().stream()
					.map((entry) -> entry.getKey() + " -> " + entry.getValue().getShortLogMessage())
					.collect(Collectors.joining("\n", "\n", "\n"));
			logger.trace("@EntityMapping registrations:" + formatted);
		}
	}

	@Override
	protected HandlerMethodArgumentResolverComposite initArgumentResolvers() {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();

		GraphQlArgumentBinder argumentBinder =
				new GraphQlArgumentBinder(getConversionService(), isFallBackOnDirectFieldAccess());

		// Annotation based
		resolvers.addResolver(new ContextValueMethodArgumentResolver());
		resolvers.addResolver(new LocalContextValueMethodArgumentResolver());
		resolvers.addResolver(new EntityArgumentMethodArgumentResolver(argumentBinder));
		resolvers.addResolver(new EntityArgumentsMethodArgumentResolver(argumentBinder));

		// Type based
		resolvers.addResolver(new DataFetchingEnvironmentMethodArgumentResolver());
		resolvers.addResolver(new DataLoaderMethodArgumentResolver());
		if (springSecurityPresent) {
			ApplicationContext context = obtainApplicationContext();
			resolvers.addResolver(new PrincipalMethodArgumentResolver());
			resolvers.addResolver(new AuthenticationPrincipalArgumentResolver(new BeanFactoryResolver(context)));
		}
		if (KotlinDetector.isKotlinPresent()) {
			resolvers.addResolver(new ContinuationHandlerMethodArgumentResolver());
		}

		return resolvers;
	}


	@Override
	@Nullable
	protected EntityMappingInfo getMappingInfo(Method method, Object handler, Class<?> handlerType) {
		EntityMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, EntityMapping.class);
		if (mapping == null) {
			return null;
		}

		String typeName = mapping.name();
		if (!StringUtils.hasText(typeName)) {
			typeName = StringUtils.capitalize(method.getName());
		}

		HandlerMethod handlerMethod = createHandlerMethod(method, handler, handlerType);
		return new EntityMappingInfo(typeName, handlerMethod);
	}

	@Override
	protected HandlerMethod getHandlerMethod(EntityMappingInfo mappingInfo) {
		return mappingInfo.handlerMethod();
	}

	/**
	 * Create {@link GraphQLSchema} via {@link SchemaTransformer}, setting up
	 * the "_entities" {@link DataFetcher} and {@link TypeResolver} for federated types.
	 * <p>Use this to supply a {@link SchemaResourceBuilder#schemaFactory(BiFunction) schemaFactory}.
	 * @param registry the existing type definition registry
	 * @param wiring the existing runtime wiring
	 */
	public GraphQLSchema createGraphQLSchema(TypeDefinitionRegistry registry, RuntimeWiring wiring) {
		return createSchemaTransformer(registry, wiring).build();
	}

	/**
	 * Alternative to {@link #createGraphQLSchema(TypeDefinitionRegistry, RuntimeWiring)}
	 * that allows calling additional methods on {@link SchemaTransformer}.
	 * @param registry the existing type definition registry
	 * @param wiring the existing runtime wiring
	 */
	public SchemaTransformer createSchemaTransformer(TypeDefinitionRegistry registry, RuntimeWiring wiring) {
		checkEntityMappings(registry);
		Assert.state(this.typeResolver != null, "afterPropertiesSet not called");
		return Federation.transform(registry, wiring)
				.fetchEntities(new EntitiesDataFetcher(this.handlerMethods, getExceptionResolver()))
				.resolveEntityType(this.typeResolver);
	}

	private void checkEntityMappings(TypeDefinitionRegistry registry) {
		List<String> unmappedEntities = new ArrayList<>();
		for (TypeDefinition<?> type : registry.types().values()) {
			if (isEntityMappingExpected(type) && !this.handlerMethods.containsKey(type.getName())) {
				unmappedEntities.add(type.getName());
			}
		}
		if (!unmappedEntities.isEmpty()) {
			throw new IllegalStateException("Unmapped entity types: " +
					unmappedEntities.stream().collect(Collectors.joining("', '", "'", "'")));
		}
	}

	/**
	 * Determine if a handler method is expected for this type: there is at least one '@key' directive
	 * whose 'resolvable' argument resolves to true (either explicitly, or if the argument is not set).
	 * @param type the type to inspect.
	 * @return true if a handler method is expected for this type
	 */
	private boolean isEntityMappingExpected(TypeDefinition<?> type) {
		List<Directive> keyDirectives = type.getDirectives("key");
		return !keyDirectives.isEmpty() && keyDirectives.stream()
			.anyMatch((keyDirective) -> {
				Argument resolvableArg = keyDirective.getArgument("resolvable");
				return resolvableArg == null ||
					(resolvableArg.getValue() instanceof BooleanValue) && ((BooleanValue) resolvableArg.getValue()).isValue();
			});
	}

	public record EntityMappingInfo(String typeName, HandlerMethod handlerMethod) {

		public boolean isBatchHandlerMethod() {
			MethodParameter returnType = handlerMethod().getReturnType();
			Class<?> clazz = returnType.getParameterType();
			ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(clazz);
			if (adapter != null) {
				if (adapter.isMultiValue()) {
					return true;
				}
				returnType = returnType.nested();
			}
			return List.class.isAssignableFrom(returnType.getNestedParameterType());
		}
	}

}
