/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.client.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.databind.type.ReferenceType;

import org.springframework.graphql.FieldValue;
import org.springframework.lang.Nullable;

class GraphQlSerializers extends Serializers.Base {

	@Nullable
	@Override
	public JsonSerializer<?> findReferenceSerializer(final SerializationConfig config, final ReferenceType refType,
					final BeanDescription beanDesc, @Nullable final TypeSerializer contentTypeSerializer,
					final JsonSerializer<Object> contentValueSerializer) {

		Class<?> raw = refType.getRawClass();
		if (FieldValue.class.isAssignableFrom(raw)) {
			boolean staticTyping = contentTypeSerializer == null && config.isEnabled(MapperFeature.USE_STATIC_TYPING);
			return new FieldValueSerializer(refType, staticTyping, contentTypeSerializer, contentValueSerializer);
		}
		else {
			return null;
		}
	}

}
