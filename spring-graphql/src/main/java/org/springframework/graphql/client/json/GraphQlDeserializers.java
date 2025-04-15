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
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.ReferenceType;

import org.springframework.graphql.FieldValue;
import org.springframework.lang.Nullable;

class GraphQlDeserializers extends Deserializers.Base {

	@Nullable
	@Override
	public JsonDeserializer<?> findReferenceDeserializer(final ReferenceType refType, final DeserializationConfig config,
					final BeanDescription beanDesc, final TypeDeserializer contentTypeDeserializer,
					final JsonDeserializer<?> contentDeserializer) {

		if (refType.hasRawClass(FieldValue.class)) {
			return new FieldValueDeserializer(refType, null, contentTypeDeserializer, contentDeserializer);
		}
		else {
			return null;
		}
	}

}
