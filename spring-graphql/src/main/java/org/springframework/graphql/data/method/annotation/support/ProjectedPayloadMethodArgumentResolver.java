/*
 * Copyright 2002-2021 the original author or authors.
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


import graphql.schema.DataFetchingEnvironment;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.lang.Nullable;

/**
 * Resolver to obtain a {@link ProjectedPayload @ProjectedPayload}
 * for {@link  DataFetchingEnvironment#getArguments()}.
 *
 * <p>Projected payloads consist of the projection interface and accessor methods.
 * Projections can be closed or open projections. Closed projections use interface
 * getter methods to access underlying properties directly. Open projection methods
 * make use of the {@code @Value} annotation to evaluate SpEL expressions against the
 * underlying {@code target} object.
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
public class ProjectedPayloadMethodArgumentResolver implements HandlerMethodArgumentResolver,
		BeanFactoryAware, BeanClassLoaderAware {

	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

	private final ArgumentMethodArgumentResolver argumentResolver;

	public ProjectedPayloadMethodArgumentResolver(@Nullable ConversionService conversionService) {
		this.argumentResolver = new ArgumentMethodArgumentResolver(conversionService){
			@Override
			protected Object convert(Object rawValue, Class<?> targetType) {
				return project(targetType, rawValue);
			}
		};
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();

		if (!type.isInterface()) {
			return false;
		}

		return AnnotatedElementUtils.findMergedAnnotation(type, ProjectedPayload.class) != null;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		if(parameter.getParameterAnnotation(Argument.class) != null){
			return argumentResolver.resolveArgument(parameter, environment);
		}

		return project(parameter.getParameterType(), environment.getArguments());
	}

	protected Object project(Class<?> projectionType, Object source){
		return this.projectionFactory.createProjection(projectionType, source);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.projectionFactory.setBeanFactory(beanFactory);
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.projectionFactory.setBeanClassLoader(classLoader);
	}
}
