/*
 * Copyright 2002-2022 the original author or authors.
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

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Source;
import org.springframework.util.Assert;

/**
 * Resolver for the source/parent of a field, obtained via
 * {@link DataFetchingEnvironment#getSource()}.
 *
 * @author Rossen Stoyanchev
 * @author Genkui Du
 * @since 1.0.0
 */
public class SourceMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterAnnotation(Source.class) != null;
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
