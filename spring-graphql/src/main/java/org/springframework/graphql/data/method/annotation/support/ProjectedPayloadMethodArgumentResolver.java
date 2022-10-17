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


import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.util.Assert;

/**
 * Resolver to obtain an {@link ProjectedPayload @ProjectedPayload},
 * either based on the complete {@link  DataFetchingEnvironment#getArguments()}
 * map, or based on a specific argument within the map when the method
 * parameter is annotated with {@code @Argument}.
 *
 * <p>Projected payloads consist of the projection interface and accessor
 * methods. Projections can be closed or open projections. Closed projections
 * use interface getter methods to access underlying properties directly.
 * Open projection methods make use of the {@code @Value} annotation to
 * evaluate SpEL expressions against the underlying {@code target} object.
 *
 * <p>For example:
 * <pre class="code">
 * &#064;ProjectedPayload
 * interface BookProjection {
 *   String getName();
 * }
 *
 * &#064;ProjectedPayload
 * interface BookProjection {
 *   &#064;Value("#{target.author + ' '  + target.name}")
 *   String getAuthorAndName();
 * }
 * </pre>
 *
 * @author Mark Paluch
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


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> type = parameter.nestedIfOptional().getNestedParameterType();
		return (type.isInterface() &&
				AnnotatedElementUtils.findMergedAnnotation(type, ProjectedPayload.class) != null);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {

		String name = (parameter.hasParameterAnnotation(Argument.class) ?
				ArgumentMethodArgumentResolver.getArgumentName(parameter) : null);

		Class<?> projectionType = parameter.getParameterType();

		boolean isOptional = parameter.isOptional();
		if (isOptional) {
			projectionType = parameter.nestedIfOptional().getNestedParameterType();
		}

		Object projectionSource = (name != null ?
				environment.getArgument(name) : environment.getArguments());

		Object value = null;
		if (!isOptional || projectionSource != null) {
			value = project(projectionType, projectionSource);
		}

		return (isOptional ? Optional.ofNullable(value) : value);
	}

	protected Object project(Class<?> projectionType, Object projectionSource){
		return this.projectionFactory.createProjection(projectionType, projectionSource);
	}

}
