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

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.ReferenceTypeSerializer;
import com.fasterxml.jackson.databind.type.ReferenceType;
import com.fasterxml.jackson.databind.util.NameTransformer;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.lang.Nullable;

import java.io.Serial;

class ArgumentValueSerializer extends ReferenceTypeSerializer<ArgumentValue<?>> {

	@Serial
	private static final long serialVersionUID = 1L;

	public ArgumentValueSerializer(final ReferenceType fullType, final boolean staticTyping,
								   @Nullable final TypeSerializer vts, final JsonSerializer<Object> ser) {

		super(fullType, staticTyping, vts, ser);
	}

	protected ArgumentValueSerializer(final ArgumentValueSerializer base, final BeanProperty property,
									  final TypeSerializer vts, final JsonSerializer<?> valueSer,
									  final NameTransformer unwrapper, final Object suppressableValue,
									  final boolean suppressNulls) {

		super(base, property, vts, valueSer, unwrapper, suppressableValue, suppressNulls);
	}

	@Override
	protected ReferenceTypeSerializer<ArgumentValue<?>> withResolved(final BeanProperty prop, final TypeSerializer vts,
																	 final JsonSerializer<?> valueSer, final NameTransformer unwrapper) {

		return new ArgumentValueSerializer(this, prop, vts, valueSer, unwrapper, _suppressableValue, _suppressNulls);
	}

	@Override
	public ReferenceTypeSerializer<ArgumentValue<?>> withContentInclusion(final Object suppressableValue, final boolean suppressNulls) {
		return new ArgumentValueSerializer(this, _property, _valueTypeSerializer,
			_valueSerializer, _unwrapper, suppressableValue, suppressNulls);
	}

	@Override
	protected boolean _isValuePresent(final ArgumentValue<?> value) {
		return !value.isOmitted();
	}

	@Nullable
	@Override
	protected Object _getReferenced(final ArgumentValue<?> value) {
		return value.value();
	}

	@Nullable
	@Override
	protected Object _getReferencedIfPresent(final ArgumentValue<?> value) {
		return value.value();
	}


}
