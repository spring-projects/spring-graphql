/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.graphql.data.method.annotation.support;


import java.util.Map;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.util.Assert;

/**
 * Resolver for a method parameter that is an interface annotated with
 * {@link ProjectedPayload @ProjectedPayload}.
 *
 * <p>By default, the projection is prepared by using the complete
 * {@link  DataFetchingEnvironment#getArguments() arguments map} as its source.
 * Add {@link Argument @Argument} with a name, if you to prepare it by using a
 * specific argument value instead as its source.
 *
 * <p>An {@code @ProjectedPayload} interface has accessor methods. In a closed
 * projection, getter methods access underlying properties directly. In an open
 * projection, getter methods make use of the {@code @Value} annotation to
 * evaluate SpEL expressions against the underlying {@code target} object.
 *
 * <p>For example:
 * <pre class="code">
 * &#064;ProjectedPayload
 * interface BookProjection {
 *
 *   String getName();
 *
 *   &#064;Value("#{target.author + ' '  + target.name}")
 *   String getAuthorAndName();
 * }
 * </pre>
 *
 * @author Mark Paluch
 * @author Rossen Stoyanchev
 *
 * @since 1.0.0
 */
public class ProjectedPayloadMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();


	/**
	 * Create a new {@link ProjectedPayloadMethodArgumentResolver} using the given context.
	 * @param applicationContext the {@link ApplicationContext} to use for bean lookup and class loading
	 */
	public ProjectedPayloadMethodArgumentResolver(ApplicationContext applicationContext) {
		Assert.notNull(applicationContext, "ApplicationContext must not be null");
		this.projectionFactory.setBeanFactory(applicationContext);
		ClassLoader classLoader = applicationContext.getClassLoader();
		if(classLoader != null) {
			this.projectionFactory.setBeanClassLoader(classLoader);
		}
	}


	/**
	 * Return underlying projection factory used by the resolver.
	 * @since 1.1.1
	 */
	protected SpelAwareProxyProjectionFactory getProjectionFactory() {
		return this.projectionFactory;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> type = getTargetType(parameter);
		return type.isInterface() && AnnotatedElementUtils.findMergedAnnotation(type, ProjectedPayload.class) != null;
	}

	private static Class<?> getTargetType(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();
		return type.equals(Optional.class) || type.equals(ArgumentValue.class) ?
				parameter.nested().getNestedParameterType() : parameter.getParameterType();
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {

		String name = parameter.hasParameterAnnotation(Argument.class) ?
				ArgumentMethodArgumentResolver.getArgumentName(parameter) : null;

		Class<?> targetType = parameter.getParameterType();
		boolean isOptional = targetType == Optional.class;
		boolean isArgumentValue = targetType == ArgumentValue.class;

		if (isOptional || isArgumentValue) {
			targetType = parameter.nested().getNestedParameterType();
		}

		Map<String, Object> arguments = environment.getArguments();
		Object rawValue = name != null ? arguments.get(name) : arguments;
		Object value = rawValue != null ? createProjection(targetType, rawValue) : null;

		if (isOptional) {
			return Optional.ofNullable(value);
		}
		else if (isArgumentValue) {
			return name != null && arguments.containsKey(name) ?
					ArgumentValue.ofNullable(value) : ArgumentValue.omitted();
		}
		else {
			return value;
		}
	}

	/**
	 * Protected method to create the projection. The default implementation
	 * delegates to the underlying {@link #getProjectionFactory() projectionFactory}.
	 * @param targetType the type to create
	 * @param rawValue a specific argument (if named via {@link Argument}
	 * or the map of arguments
	 * @return the created project instance
	 */
	protected Object createProjection(Class<?> targetType, Object rawValue){
		return this.projectionFactory.createProjection(targetType, rawValue);
	}

}
