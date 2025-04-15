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

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.ReferenceTypeSerializer;
import com.fasterxml.jackson.databind.type.ReferenceType;
import com.fasterxml.jackson.databind.util.NameTransformer;

import org.springframework.graphql.FieldValue;
import org.springframework.lang.Nullable;

/**
 * {@link ReferenceTypeSerializer} that serializes {@link FieldValue} values as:
 * <ul>
 *     <li>the embedded value if it is present and not {@literal null}.
 *     <li>{@literal null} if the embedded value is present and {@literal null}.
 *     <li>an empty value if the embedded value is not present.
 * </ul>
 * @author James Bodkin
 */
class FieldValueSerializer extends ReferenceTypeSerializer<FieldValue<?>> {

	@Serial
	private static final long serialVersionUID = 1L;

	FieldValueSerializer(final ReferenceType fullType, final boolean staticTyping,
					@Nullable final TypeSerializer vts, final JsonSerializer<Object> ser) {

		super(fullType, staticTyping, vts, ser);
	}

	protected FieldValueSerializer(final FieldValueSerializer base, final BeanProperty property,
					final TypeSerializer vts, final JsonSerializer<?> valueSer,
					final NameTransformer unwrapper, final Object suppressableValue,
					final boolean suppressNulls) {

		super(base, property, vts, valueSer, unwrapper, suppressableValue, suppressNulls);
	}

	@Override
	protected ReferenceTypeSerializer<FieldValue<?>> withResolved(final BeanProperty prop, final TypeSerializer vts,
					final JsonSerializer<?> valueSer, final NameTransformer unwrapper) {

		return new FieldValueSerializer(this, prop, vts, valueSer, unwrapper, _suppressableValue, _suppressNulls);
	}

	@Override
	public ReferenceTypeSerializer<FieldValue<?>> withContentInclusion(final Object suppressableValue, final boolean suppressNulls) {
		return new FieldValueSerializer(this, _property, _valueTypeSerializer,
				_valueSerializer, _unwrapper, suppressableValue, suppressNulls);
	}

	@Override
	protected boolean _isValuePresent(final FieldValue<?> value) {
		return !value.isOmitted();
	}

	@Nullable
	@Override
	protected Object _getReferenced(final FieldValue<?> value) {
		return value.value();
	}

	@Nullable
	@Override
	protected Object _getReferencedIfPresent(final FieldValue<?> value) {
		return value.value();
	}

}
