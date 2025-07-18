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

package org.springframework.graphql;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingException;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link MappingProvider} for Jackson 3.x.
 * @author Brian Clozel
 */
class JacksonMappingProvider implements MappingProvider {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public <T> @Nullable T map(@Nullable Object source, Class<T> targetType, Configuration configuration) {
		if (source == null) {
			return null;
		}
		try {
			return this.objectMapper.convertValue(source, targetType);
		}
		catch (Exception exc) {
			throw new MappingException(exc);
		}

	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T map(@Nullable Object source, final TypeRef<T> targetType, Configuration configuration) {
		if (source == null) {
			return null;
		}
		JavaType type = this.objectMapper.getTypeFactory().constructType(targetType.getType());

		try {
			return (T) this.objectMapper.convertValue(source, type);
		}
		catch (Exception exc) {
			throw new MappingException(exc);
		}

	}
}
