/*
 * Copyright 2002-2025 the original author or authors.
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

package org.springframework.graphql.data.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.ReferenceTypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.lang.Nullable;

import java.io.Serial;
import java.io.Serializable;

class ArgumentValueDeserializer extends ReferenceTypeDeserializer<ArgumentValue<?>> {

	@Serial
	private static final long serialVersionUID = 1L;

	public ArgumentValueDeserializer(final JavaType fullType, @Nullable final ValueInstantiator vi,
									 final TypeDeserializer typeDeser, final JsonDeserializer<?> deser) {

		super(fullType, vi, typeDeser, deser);
	}

	@Override
	protected ReferenceTypeDeserializer<ArgumentValue<?>> withResolved(final TypeDeserializer typeDeser,
																	   final JsonDeserializer<?> valueDeser) {

		return new ArgumentValueDeserializer(_fullType, _valueInstantiator, typeDeser, valueDeser);
	}

	@Override
	public ArgumentValue<? extends Serializable> getNullValue(final DeserializationContext ctxt) throws JsonMappingException {
		return ArgumentValue.ofNullable((Serializable) _valueDeserializer.getNullValue(ctxt));
	}

	@Override
	public Object getEmptyValue(final DeserializationContext ctxt) throws JsonMappingException {
		return getNullValue(ctxt);
	}

	@Override
	public Object getAbsentValue(final DeserializationContext ctxt) {
		return ArgumentValue.omitted();
	}

	@Override
	public ArgumentValue<?> referenceValue(final Object contents) {
		return ArgumentValue.ofNullable((Serializable) contents);
	}

	@Nullable
	@Override
	public Object getReferenced(final ArgumentValue<?> value) {
		return value.value();
	}

	@Override
	public ArgumentValue<?> updateReference(final ArgumentValue<?> value, final Object contents) {
		return ArgumentValue.ofNullable((Serializable) contents);
	}

}
