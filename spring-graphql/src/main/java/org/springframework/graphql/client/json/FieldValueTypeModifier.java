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

import java.lang.reflect.Type;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.ReferenceType;
import com.fasterxml.jackson.databind.type.TypeBindings;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.TypeModifier;

import org.springframework.graphql.FieldValue;

/**
 * {@link TypeModifier} that upgrades {@link FieldValue} types to {@link ReferenceType}.
 *
 * @author James Bodkin
 */
class FieldValueTypeModifier extends TypeModifier {

	@Override
	public JavaType modifyType(final JavaType type, final Type jdkType, final TypeBindings context, final TypeFactory typeFactory) {
		Class<?> raw = type.getRawClass();
		if (!type.isReferenceType() && !type.isContainerType() && raw == FieldValue.class) {
			JavaType refType = type.containedTypeOrUnknown(0);
			return ReferenceType.upgradeFrom(type, refType);
		}
		else {
			return type;
		}
	}

}
