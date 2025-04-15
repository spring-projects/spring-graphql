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

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.ReferenceTypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.ser.std.ReferenceTypeSerializer;

import org.springframework.graphql.FieldValue;
import org.springframework.lang.Nullable;

/**
 * {@link ReferenceTypeSerializer} that deserializes JSON values as {@link FieldValue}:
 * <ul>
 *     <li>a {@link FieldValue#isEmpty() non empty FieldValue} when the JSON key is present and its value is not {@literal null}.
 *     <li>an {@link FieldValue#isEmpty() empty FieldValue} when the JSON key is present and its value is {@literal null}.
 *     <li>an {@link FieldValue#isOmitted() ommitted FieldValue} when the JSON key is not present.
 * </ul>
 * @author James Bodkin
 */
class FieldValueDeserializer extends ReferenceTypeDeserializer<FieldValue<?>> {

	@Serial
	private static final long serialVersionUID = 1L;

	FieldValueDeserializer(final JavaType fullType, @Nullable final ValueInstantiator vi,
					final TypeDeserializer typeDeser, final JsonDeserializer<?> deser) {

		super(fullType, vi, typeDeser, deser);
	}

	@Override
	protected ReferenceTypeDeserializer<FieldValue<?>> withResolved(final TypeDeserializer typeDeser,
					final JsonDeserializer<?> valueDeser) {

		return new FieldValueDeserializer(_fullType, _valueInstantiator, typeDeser, valueDeser);
	}

	@Override
	public FieldValue<? extends Serializable> getNullValue(final DeserializationContext ctxt) throws JsonMappingException {
		return FieldValue.ofNullable((Serializable) _valueDeserializer.getNullValue(ctxt));
	}

	@Override
	public Object getEmptyValue(final DeserializationContext ctxt) throws JsonMappingException {
		return getNullValue(ctxt);
	}

	@Override
	public Object getAbsentValue(final DeserializationContext ctxt) {
		return FieldValue.omitted();
	}

	@Override
	public FieldValue<?> referenceValue(final Object contents) {
		return FieldValue.ofNullable((Serializable) contents);
	}

	@Nullable
	@Override
	public Object getReferenced(final FieldValue<?> value) {
		return value.value();
	}

	@Override
	public FieldValue<?> updateReference(final FieldValue<?> value, final Object contents) {
		return FieldValue.ofNullable((Serializable) contents);
	}

}
