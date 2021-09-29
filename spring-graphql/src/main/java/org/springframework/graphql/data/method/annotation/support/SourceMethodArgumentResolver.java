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

import java.util.Collection;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.util.Assert;

/**
 * Resolver for the source/parent of a field, obtained via
 * {@link DataFetchingEnvironment#getSource()}.
 *
 * <p>This resolver supports any non-simple value type, also excluding arrays
 * and collections, and therefore must be ordered last, in a fallback mode,
 * allowing other resolvers to resolve the argument first.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class SourceMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();
		return (!BeanUtils.isSimpleValueType(type) && !type.isArray() && !Collection.class.isAssignableFrom(type));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {
		Object source = environment.getSource();
		Assert.isInstanceOf(parameter.getParameterType(), source,
				"The declared parameter of type '" + parameter.getParameterType() + "' " +
						"does not match the type of the source Object '" + source.getClass() + "'.");
		return source;
	}

}
