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

import java.net.URI;
import java.net.URL;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Resolver for the source/parent of a field, obtained via
 * {@link DataFetchingEnvironment#getSource()}.
 *
 * <p>This resolver supports any type excluding enums, dates, arrays,
 *
 * wide range of types, including any non-simple
 * type, along with any CharSequence, or Number. Hence, it must come last in
 * the order or resolvers, as a fallback after all others.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class SourceMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();
		return (!isExcludedSimpleValueType(type) && !type.isArray() && !Collection.class.isAssignableFrom(type));
	}

	private static boolean isExcludedSimpleValueType(Class<?> type) {
		// Same as BeanUtils.isSimpleValueType except for CharSequence and Number
		return (Void.class != type && void.class != type &&
				(ClassUtils.isPrimitiveOrWrapper(type) ||
						Enum.class.isAssignableFrom(type) ||
						Date.class.isAssignableFrom(type) ||
						Temporal.class.isAssignableFrom(type) ||
						URI.class == type ||
						URL.class == type ||
						Locale.class == type ||
						Class.class == type));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {
		Object source = environment.getSource();
		if (source == null) {
			throw new IllegalStateException(formatArgumentError(parameter,
					" was not recognized by any resolver and there is no source/parent either. " +
							"Please, refer to the documentation for the full list of supported parameters."));
		}
		if (!parameter.getParameterType().isInstance(source)) {
			throw new IllegalStateException(formatArgumentError(parameter,
					" does not match the source Object type '" + source.getClass() + "'."));
		}
		return source;
	}

	private static String formatArgumentError(MethodParameter param, String message) {
		return "Parameter [" + param.getParameterIndex() + "] in " +
				param.getExecutable().toGenericString() + (StringUtils.hasText(message) ? ": " + message : "");
	}

}
