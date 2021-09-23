/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.idl.RuntimeWiring;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.InvocableHandlerMethod;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link RuntimeWiringConfigurer} that detects {@link SchemaMapping @SchemaMapping}
 * annotated handler methods in {@link Controller @Controller} classes and
 * registers them as {@link DataFetcher}s.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class AnnotatedDataFetcherConfigurer
		implements ApplicationContextAware, InitializingBean, RuntimeWiringConfigurer {

	private final static Log logger = LogFactory.getLog(AnnotatedDataFetcherConfigurer.class);


	/**
	 * Bean name prefix for target beans behind scoped proxies. Used to exclude those
	 * targets from handler method detection, in favor of the corresponding proxies.
	 * <p>We're not checking the autowire-candidate status here, which is how the
	 * proxy target filtering problem is being handled at the autowiring level,
	 * since autowire-candidate may have been turned to {@code false} for other
	 * reasons, while still expecting the bean to be eligible for handler methods.
	 * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
	 * but duplicated here to avoid a hard dependency on the spring-aop module.
	 */
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

	private static final ResolvableType MAP_RESOLVABLE_TYPE =
			ResolvableType.forType(new ParameterizedTypeReference<Map<String, Object>>() {});


	@Nullable
	private ApplicationContext applicationContext;

	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	protected final ApplicationContext obtainApplicationContext() {
		Assert.state(this.applicationContext != null, "No ApplicationContext");
		return this.applicationContext;
	}


	@Override
	public void afterPropertiesSet() {
		this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
		this.argumentResolvers.addResolver(new ArgumentMapMethodArgumentResolver());
		this.argumentResolvers.addResolver(new ArgumentMethodArgumentResolver());
		this.argumentResolvers.addResolver(new DataFetchingEnvironmentMethodArgumentResolver());
		this.argumentResolvers.addResolver(new DataLoaderMethodArgumentResolver());

		if (KotlinDetector.isKotlinPresent()) {
			this.argumentResolvers.addResolver(new ContinuationHandlerMethodArgumentResolver());
		}

		// This works as a fallback, after all other resolvers
		this.argumentResolvers.addResolver(new SourceMethodArgumentResolver());
	}

	@Override
	public void configure(RuntimeWiring.Builder builder) {
		Assert.state(this.argumentResolvers != null, "`argumentResolvers` is not initialized");
		findHandlerMethods().forEach((info) -> {
			FieldCoordinates coordinates = info.getCoordinates();
			HandlerMethod handlerMethod = info.getHandlerMethod();
			DataFetcher<?> dataFetcher = new SchemaMappingDataFetcher(coordinates, handlerMethod, this.argumentResolvers);
			builder.type(coordinates.getTypeName(), typeBuilder ->
					typeBuilder.dataFetcher(coordinates.getFieldName(), dataFetcher));
		});
	}

	/**
	 * Scan beans in the ApplicationContext, detect and prepare a map of handler methods.
	 */
	private Collection<MappingInfo> findHandlerMethods() {
		ApplicationContext context = obtainApplicationContext();
		Map<FieldCoordinates, MappingInfo> result = new HashMap<>();
		for (String beanName : context.getBeanNamesForType(Object.class)) {
			if (beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				continue;
			}
			Class<?> beanType = null;
			try {
				beanType = context.getType(beanName);
			}
			catch (Throwable ex) {
				// An unresolvable bean type, probably from a lazy bean - let's ignore it.
				if (logger.isTraceEnabled()) {
					logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
				}
			}
			if (beanType == null || !AnnotatedElementUtils.hasAnnotation(beanType, Controller.class)) {
				continue;
			}
			Class<?> beanClass = context.getType(beanName);
			findHandlerMethods(beanName, beanClass).forEach((info) -> {
				HandlerMethod handlerMethod = info.getHandlerMethod();
				MappingInfo existing = result.put(info.getCoordinates(), info);
				if (existing != null && !existing.getHandlerMethod().equals(handlerMethod)) {
					throw new IllegalStateException(
							"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
									handlerMethod + "\nto " + info.getCoordinates() + ": There is already '" +
									existing.getHandlerMethod().getBean() + "' bean method\n" + existing + " mapped.");
				}
			});
		}
		return result.values();
	}

	private Collection<MappingInfo> findHandlerMethods(Object handler, @Nullable Class<?> handlerClass) {
		if (handlerClass == null) {
			return Collections.emptyList();
		}

		Class<?> userClass = ClassUtils.getUserClass(handlerClass);
		Map<Method, MappingInfo> map =
				MethodIntrospector.selectMethods(userClass, (Method method) -> getMappingInfo(method, handler, userClass));

		Collection<MappingInfo> mappingInfos = map.values();

		if (logger.isTraceEnabled() && !mappingInfos.isEmpty()) {
			logger.trace(formatMappings(userClass, mappingInfos));
		}

		return mappingInfos;
	}

	@Nullable
	private MappingInfo getMappingInfo(Method method, Object handler, Class<?> handlerType) {
		SchemaMapping annotation = AnnotatedElementUtils.findMergedAnnotation(method, SchemaMapping.class);
		if (annotation == null) {
			return null;
		}

		String typeName = annotation.typeName();
		String field = (StringUtils.hasText(annotation.field()) ? annotation.field() : method.getName());
		HandlerMethod handlerMethod = createHandlerMethod(method, handler, handlerType);

		if (!StringUtils.hasText(typeName)) {
			SchemaMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handlerType, SchemaMapping.class);
			if (mapping != null) {
				typeName = annotation.typeName();
			}
		}

		if (!StringUtils.hasText(typeName)) {
			Assert.state(this.argumentResolvers != null, "`argumentResolvers` is not initialized");
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				HandlerMethodArgumentResolver resolver = this.argumentResolvers.getArgumentResolver(parameter);
				if (resolver instanceof SourceMethodArgumentResolver) {
					typeName = parameter.getParameterType().getSimpleName();
					break;
				}
			}
		}

		Assert.hasText(typeName,
				"No parentType specified, and a source/parent method argument was also not found: "  +
						handlerMethod.getShortLogMessage());

		return new MappingInfo(typeName, field, handlerMethod);
	}

	private HandlerMethod createHandlerMethod(Method method, Object handler, Class<?> handlerType) {
		Method invocableMethod = AopUtils.selectInvocableMethod(method, handlerType);
		return (handler instanceof String ?
				new HandlerMethod((String) handler, obtainApplicationContext().getAutowireCapableBeanFactory(), invocableMethod) :
				new HandlerMethod(handler, invocableMethod));
	}

	private String formatMappings(Class<?> handlerType, Collection<MappingInfo> mappings) {
		String formattedType = Arrays.stream(ClassUtils.getPackageName(handlerType).split("\\."))
				.map(p -> p.substring(0, 1))
				.collect(Collectors.joining(".", "", "." + handlerType.getSimpleName()));
		return mappings.stream()
				.map(mappingInfo -> {
					Method method = mappingInfo.getHandlerMethod().getMethod();
					String methodParameters = Arrays.stream(method.getParameterTypes())
							.map(Class::getSimpleName)
							.collect(Collectors.joining(",", "(", ")"));
					return mappingInfo.getCoordinates() + " => "  + method.getName() + methodParameters;
				})
				.collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
	}


	private static class MappingInfo {

		private final FieldCoordinates coordinates;

		private final HandlerMethod handlerMethod;

		public MappingInfo(String typeName, String field, HandlerMethod handlerMethod) {
			this.coordinates = FieldCoordinates.coordinates(typeName, field);
			this.handlerMethod = handlerMethod;
		}

		public FieldCoordinates getCoordinates() {
			return this.coordinates;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}
	}


	/**
	 * {@link DataFetcher} that wrap and invokes a {@link HandlerMethod}.
	 */
	static class SchemaMappingDataFetcher implements DataFetcher<Object> {

		private final FieldCoordinates coordinates;

		private final HandlerMethod handlerMethod;

		private final HandlerMethodArgumentResolverComposite argumentResolvers;


		public SchemaMappingDataFetcher(FieldCoordinates coordinates, HandlerMethod handlerMethod,
				HandlerMethodArgumentResolverComposite resolvers) {

			this.coordinates = coordinates;
			this.handlerMethod = handlerMethod;
			this.argumentResolvers = resolvers;
		}


		/**
		 * Return the {@link FieldCoordinates} the HandlerMethod is mapped to.
		 */
		public FieldCoordinates getCoordinates() {
			return this.coordinates;
		}

		/**
		 * Return the {@link HandlerMethod} used to fetch data.
		 */
		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}


		@Override
		@SuppressWarnings("ConstantConditions")
		public Object get(DataFetchingEnvironment environment) throws Exception {

			InvocableHandlerMethod invocable =
					new InvocableHandlerMethod(this.handlerMethod.createWithResolvedBean(), this.argumentResolvers);

			return invocable.invoke(environment);
		}
	}

}
