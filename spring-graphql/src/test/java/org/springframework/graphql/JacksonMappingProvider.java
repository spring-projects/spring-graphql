/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
