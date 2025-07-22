/*
 * Copyright 2025-present the original author or authors.
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

import java.lang.reflect.Type;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.Serializers;
import tools.jackson.databind.ser.std.ReferenceTypeSerializer;
import tools.jackson.databind.type.ReferenceType;
import tools.jackson.databind.type.TypeBindings;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.type.TypeModifier;
import tools.jackson.databind.util.NameTransformer;

import org.springframework.graphql.data.ArgumentValue;

/**
 * {@link Module Jackson module} for JSON support in GraphQL clients.
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
public class GraphQlJacksonModule extends JacksonModule {

	@Override
	public String getModuleName() {
		return GraphQlJacksonModule.class.getName();
	}

	@Override
	public Version version() {
		return Version.unknownVersion();
	}

	@Override
	public void setupModule(SetupContext context) {
		context.addSerializers(new GraphQlSerializers());
		context.addTypeModifier(new ArgumentValueTypeModifier());

		context.configOverride(ArgumentValue.class)
				.setInclude(JsonInclude.Value.empty().withValueInclusion(JsonInclude.Include.NON_ABSENT));
	}

	static class GraphQlSerializers extends Serializers.Base {

		@Override
		public @Nullable ValueSerializer<?> findReferenceSerializer(SerializationConfig config, ReferenceType type,
																	BeanDescription.Supplier beanDescRef, JsonFormat.Value formatOverrides,
																	@Nullable TypeSerializer contentTypeSerializer, ValueSerializer<Object> contentValueSerializer) {

			Class<?> raw = type.getRawClass();
			if (ArgumentValue.class.isAssignableFrom(raw)) {
				boolean staticTyping = contentTypeSerializer == null && config.isEnabled(MapperFeature.USE_STATIC_TYPING);
				return new ArgumentValueSerializer(type, staticTyping, contentTypeSerializer, contentValueSerializer);
			}
			else {
				return null;
			}
		}

	}

	static class ArgumentValueTypeModifier extends TypeModifier {

		@Override
		public JavaType modifyType(JavaType type, Type jdkType, TypeBindings context, TypeFactory typeFactory) {
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

		ArgumentValueSerializer(ReferenceType fullType, boolean staticTyping,
								@Nullable TypeSerializer vts, ValueSerializer<Object> ser) {
			super(fullType, staticTyping, vts, ser);
		}

		ArgumentValueSerializer(ReferenceTypeSerializer<?> base, BeanProperty property, TypeSerializer vts,
									ValueSerializer<?> valueSer, NameTransformer unwrapper,
									Object suppressableValue, boolean suppressNulls) {
			super(base, property, vts, valueSer, unwrapper, suppressableValue, suppressNulls);
		}

		@Override
		protected ReferenceTypeSerializer<ArgumentValue<?>> withResolved(BeanProperty prop, TypeSerializer vts, ValueSerializer<?> valueSer, NameTransformer unwrapper) {
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
