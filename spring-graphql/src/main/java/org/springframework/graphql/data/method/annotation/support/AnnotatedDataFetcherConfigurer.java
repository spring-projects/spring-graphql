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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.converter.GenericHttpMessageConverter;
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

	@Nullable
	private GenericHttpMessageConverter<Object> jsonMessageConverter;

	@Nullable
	private Encoder<Object> jsonEncoder;

	@Nullable
	private Decoder<Object> jsonDecoder;


	/**
	 * Configure the {@link org.springframework.http.converter.HttpMessageConverter}
	 * to use to convert input arguments obtained from the
	 * {@link DataFetchingEnvironment} and converted to the type of a declared
	 * {@link org.springframework.graphql.data.method.annotation.Argument @Argument}
	 * method parameter.
	 * <p>This method is mutually exclusive with
	 * {@link #setServerCodecConfigurer(ServerCodecConfigurer)} and is convenient
	 * for use in a Spring MVC application but both variant can be used without
	 * much difference.
	 * @param converter the converter to use.
	 */
	public void setJsonMessageConverter(@Nullable GenericHttpMessageConverter<Object> converter) {
		this.jsonMessageConverter = converter;
	}

	/**
	 * Variant of {@link #setJsonMessageConverter(GenericHttpMessageConverter)}
	 * to use an {@link Encoder} and {@link Decoder} to convert input arguments.
	 * <p>This method is mutually exclusive with
	 * {@link #setJsonMessageConverter(GenericHttpMessageConverter)} and is
	 * convenient for use in a Spring WebFlux application but both variant can
	 * be used without much difference.
	 */
	@SuppressWarnings("unchecked")
	public void setServerCodecConfigurer(@Nullable ServerCodecConfigurer configurer) {
		if (configurer == null) {
			this.jsonDecoder = null;
			this.jsonEncoder = null;
			return;
		}
		this.jsonDecoder = configurer.getReaders().stream()
				.filter((reader) -> reader.canRead(MAP_RESOLVABLE_TYPE, MediaType.APPLICATION_JSON))
				.map((reader) -> ((DecoderHttpMessageReader<Object>) reader).getDecoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No Decoder for JSON"));
		this.jsonEncoder = configurer.getWriters().stream()
				.filter((writer) -> writer.canWrite(MAP_RESOLVABLE_TYPE, MediaType.APPLICATION_JSON))
				.map((writer) -> ((EncoderHttpMessageWriter<Object>) writer).getEncoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No Encoder for JSON"));
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}


	@Override
	public void afterPropertiesSet() {
		this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
		this.argumentResolvers.addResolver(initInputArgumentMethodArgumentResolver());
		this.argumentResolvers.addResolver(new ArgumentMapMethodArgumentResolver());
		this.argumentResolvers.addResolver(new DataFetchingEnvironmentMethodArgumentResolver());
		this.argumentResolvers.addResolver(new DataLoaderMethodArgumentResolver());

		// This works as a fallback, after all other resolvers
		this.argumentResolvers.addResolver(new SourceMethodArgumentResolver());
	}

	private ArgumentMethodArgumentResolver initInputArgumentMethodArgumentResolver() {
		ArgumentMethodArgumentResolver argumentResolver;
		if (this.jsonMessageConverter != null) {
			argumentResolver = new ArgumentMethodArgumentResolver(this.jsonMessageConverter);
		}
		else if (this.jsonEncoder != null && this.jsonDecoder != null) {
			argumentResolver = new ArgumentMethodArgumentResolver(this.jsonDecoder, this.jsonEncoder);
		}
		else {
			throw new IllegalArgumentException(
					"Neither HttpMessageConverter nor Encoder/Decoder for JSON provided");
		}
		return argumentResolver;
	}


	@Override
	public void configure(RuntimeWiring.Builder builder) {
		Assert.notNull(this.applicationContext, "ApplicationContext is required");
		Assert.notNull(this.argumentResolvers, "`argumentResolvers` are required");

		detectHandlerMethods().forEach((coordinates, handlerMethod) -> {
			DataFetcher<?> dataFetcher = new AnnotatedDataFetcher(coordinates, handlerMethod, this.argumentResolvers);
			builder.type(coordinates.getTypeName(), typeBuilder ->
					typeBuilder.dataFetcher(coordinates.getFieldName(), dataFetcher));
		});
	}

	/**
	 * Scan beans in the ApplicationContext, detect and prepare a map of handler methods.
	 */
	private Map<FieldCoordinates, HandlerMethod> detectHandlerMethods() {
		Map<FieldCoordinates, HandlerMethod> result = new HashMap<>();
		for (String beanName : this.applicationContext.getBeanNamesForType(Object.class)) {
			if (beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				continue;
			}
			Class<?> beanType = null;
			try {
				beanType = this.applicationContext.getType(beanName);
			}
			catch (Throwable ex) {
				// An unresolvable bean type, probably from a lazy bean - let's ignore it.
				if (logger.isTraceEnabled()) {
					logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
				}
			}
			if (beanType == null || !isHandler(beanType)) {
				continue;
			}
			detectHandlerMethodsOnBean(beanName).forEach((coordinates, handlerMethod) -> {
				HandlerMethod existing = result.put(coordinates, handlerMethod);
				if (existing != null && !existing.equals(handlerMethod)) {
					throw new IllegalStateException(
							"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
									handlerMethod + "\nto " + coordinates + ": There is already '" +
									existing.getBean() + "' bean method\n" + existing + " mapped.");
				}
			});
		}
		return result;
	}

	private boolean isHandler(Class<?> beanType) {
		return (AnnotatedElementUtils.hasAnnotation(beanType, Controller.class) ||
				AnnotatedElementUtils.hasAnnotation(beanType, SchemaMapping.class));
	}

	private Map<FieldCoordinates, HandlerMethod> detectHandlerMethodsOnBean(Object handler) {
		Class<?> beanClass = (handler instanceof String ?
				this.applicationContext.getType((String) handler) : handler.getClass());
		if (beanClass == null) {
			return Collections.emptyMap();
		}

		Class<?> userClass = ClassUtils.getUserClass(beanClass);
		Map<Method, FieldCoordinates> methodsMap =
				MethodIntrospector.selectMethods(userClass, (Method method) -> getCoordinates(method, userClass));
		if (methodsMap.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<FieldCoordinates, HandlerMethod> result = new LinkedHashMap<>(methodsMap.size());
		for (Map.Entry<Method, FieldCoordinates> entry : methodsMap.entrySet()) {
			Method method = AopUtils.selectInvocableMethod(entry.getKey(), userClass);
			HandlerMethod handlerMethod = (handler instanceof String ?
					new HandlerMethod((String) handler, this.applicationContext.getAutowireCapableBeanFactory(), method) :
					new HandlerMethod(handler, method));
			FieldCoordinates coordinates = entry.getValue();
			coordinates = updateCoordinates(coordinates, handlerMethod);
			result.put(coordinates, handlerMethod);
		}

		if (logger.isTraceEnabled()) {
			logger.trace(formatMappings(userClass, result));
		}

		return result;
	}

	@Nullable
	private FieldCoordinates getCoordinates(Method method, Class<?> handlerType) {
		QueryMapping query = AnnotatedElementUtils.findMergedAnnotation(method, QueryMapping.class);
		if (query != null) {
			String name = (StringUtils.hasText(query.name()) ? query.name() : method.getName());
			return FieldCoordinates.coordinates("Query", name);
		}
		MutationMapping mutation = AnnotatedElementUtils.findMergedAnnotation(method, MutationMapping.class);
		if (mutation != null) {
			String name = (StringUtils.hasText(mutation.name()) ? mutation.name() : method.getName());
			return FieldCoordinates.coordinates("Mutation", name);
		}
		SubscriptionMapping subscription = AnnotatedElementUtils.findMergedAnnotation(method, SubscriptionMapping.class);
		if (subscription != null) {
			String name = (StringUtils.hasText(subscription.name()) ? subscription.name() : method.getName());
			return FieldCoordinates.coordinates("Subscription", name);
		}
		SchemaMapping schemaMapping = AnnotatedElementUtils.findMergedAnnotation(method, SchemaMapping.class);
		if (schemaMapping != null) {
			String typeName = schemaMapping.typeName();
			String field = schemaMapping.field();
			if (!StringUtils.hasText(typeName)) {
				schemaMapping = AnnotatedElementUtils.findMergedAnnotation(handlerType, SchemaMapping.class);
				if (schemaMapping != null) {
					typeName = schemaMapping.typeName();
				}
			}
			return FieldCoordinates.coordinates(typeName, field);
		}
		return null;
	}

	private FieldCoordinates updateCoordinates(FieldCoordinates coordinates, HandlerMethod handlerMethod) {
		boolean hasTypeName = StringUtils.hasText(coordinates.getTypeName());
		boolean hasFieldName = StringUtils.hasText(coordinates.getFieldName());
		if (hasTypeName && hasFieldName) {
			return coordinates;
		}
		String typeName = coordinates.getTypeName();
		if (!hasTypeName) {
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				HandlerMethodArgumentResolver resolver = this.argumentResolvers.getArgumentResolver(parameter);
				if (resolver instanceof SourceMethodArgumentResolver) {
					typeName = parameter.getParameterType().getSimpleName();
					break;
				}
			}
			Assert.hasText(typeName,
					"No parentType specified, and a source/container method argument was also not found: "  +
							handlerMethod.getShortLogMessage());
		}
		return FieldCoordinates.coordinates(typeName,
				(hasFieldName ? coordinates.getFieldName() : handlerMethod.getMethod().getName()));
	}

	private String formatMappings(Class<?> handlerType, Map<FieldCoordinates, HandlerMethod> mappings) {
		String formattedType = Arrays.stream(ClassUtils.getPackageName(handlerType).split("\\."))
				.map(p -> p.substring(0, 1))
				.collect(Collectors.joining(".", "", "." + handlerType.getSimpleName()));
		return mappings.entrySet().stream()
				.map(entry -> {
					Method method = entry.getValue().getMethod();
					String methodParameters = Arrays.stream(method.getParameterTypes())
							.map(Class::getSimpleName)
							.collect(Collectors.joining(",", "(", ")"));
					return entry.getKey() + " => "  + method.getName() + methodParameters;
				})
				.collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
	}

}
