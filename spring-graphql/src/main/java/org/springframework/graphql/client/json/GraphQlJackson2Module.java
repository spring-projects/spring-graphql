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

package org.springframework.graphql.client.json;

import java.io.Serial;
import java.lang.reflect.Type;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.databind.ser.std.ReferenceTypeSerializer;
import com.fasterxml.jackson.databind.type.ReferenceType;
import com.fasterxml.jackson.databind.type.TypeBindings;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.TypeModifier;
import com.fasterxml.jackson.databind.util.NameTransformer;
import org.jspecify.annotations.Nullable;

import org.springframework.graphql.data.ArgumentValue;


/**
 * {@link Module Jackson 2.x module} for JSON support in GraphQL clients.
 * <p>This module ships with the following features:
 * <ul>
 *   <li>Manage {@link ArgumentValue} types as {@link ReferenceType}, similar to {@link java.util.Optional}
 *   <li>Serializing values contained in {@link ArgumentValue} reference types
 * </ul>
 *
 * @author James Bodkin
 * @author Brian Clozel
 * @since 2.0.0
 */
public class GraphQlJackson2Module extends Module {

	@Override
	public String getModuleName() {
		return GraphQlJackson2Module.class.getName();
	}

	@Override
	public Version version() {
		return Version.unknownVersion();
	}

	@Override
	public void setupModule(final SetupContext context) {
		context.addSerializers(new GraphQlSerializers());
		context.addTypeModifier(new ArgumentValueTypeModifier());

		context.configOverride(ArgumentValue.class)
				.setInclude(JsonInclude.Value.empty().withValueInclusion(JsonInclude.Include.NON_ABSENT));
	}

	static class GraphQlSerializers extends Serializers.Base {

		@Override
		public @Nullable JsonSerializer<?> findReferenceSerializer(final SerializationConfig config, final ReferenceType refType,
																final BeanDescription beanDesc, final @Nullable TypeSerializer contentTypeSerializer,
																final JsonSerializer<Object> contentValueSerializer) {

			Class<?> raw = refType.getRawClass();
			if (ArgumentValue.class.isAssignableFrom(raw)) {
				boolean staticTyping = contentTypeSerializer == null && config.isEnabled(MapperFeature.USE_STATIC_TYPING);
				return new ArgumentValueSerializer(refType, staticTyping, contentTypeSerializer, contentValueSerializer);
			}
			else {
				return null;
			}
		}

	}

	static class ArgumentValueTypeModifier extends TypeModifier {

		@Override
		public JavaType modifyType(final JavaType type, final Type jdkType, final TypeBindings context, final TypeFactory typeFactory) {
			Class<?> raw = type.getRawClass();
			if (!type.isReferenceType() && !type.isContainerType() && raw == ArgumentValue.class) {
				JavaType refType = type.containedTypeOrUnknown(0);
				return ReferenceType.upgradeFrom(type, refType);
			}
			else {
				return type;
			}
		}

	}

	/**
	 * {@link ReferenceTypeSerializer} that serializes {@link ArgumentValue} values as:
	 * <ul>
	 *     <li>the embedded value if it is present and not {@literal null}.
	 *     <li>{@literal null} if the embedded value is present and {@literal null}.
	 *     <li>an empty value if the embedded value is not present.
	 * </ul>
	 */
	static class ArgumentValueSerializer extends ReferenceTypeSerializer<ArgumentValue<?>> {

		@Serial
		private static final long serialVersionUID = 1L;

		ArgumentValueSerializer(final ReferenceType fullType, final boolean staticTyping,
								final @Nullable TypeSerializer vts, final JsonSerializer<Object> ser) {

			super(fullType, staticTyping, vts, ser);
		}

		ArgumentValueSerializer(final ArgumentValueSerializer base, final BeanProperty property,
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

		@Override
		protected @Nullable Object _getReferenced(final ArgumentValue<?> value) {
			return value.value();
		}

		@Override
		protected @Nullable Object _getReferencedIfPresent(final ArgumentValue<?> value) {
			return value.value();
		}

	}

}
