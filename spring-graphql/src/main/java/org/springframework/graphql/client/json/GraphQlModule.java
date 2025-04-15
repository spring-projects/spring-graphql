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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.type.ReferenceType;

import org.springframework.graphql.FieldValue;

/**
 * {@link Module Jackson module} for JSON support in GraphQL clients.
 * <p>This module ships with the following features:
 * <ul>
 *   <li>Manage {@link FieldValue} types as {@link ReferenceType}, similar to {@link java.util.Optional}
 *   <li>Serializing and Deserializing values contained in {@link FieldValue} reference types
 * </ul>
 * @author James Bodkin
 * @since 1.4.0
 */
public class GraphQlModule extends Module {

	@Override
	public String getModuleName() {
		return GraphQlModule.class.getName();
	}

	@Override
	public Version version() {
		return Version.unknownVersion();
	}

	@Override
	public void setupModule(final SetupContext context) {
		context.addSerializers(new GraphQlSerializers());
		context.addDeserializers(new GraphQlDeserializers());
		context.addTypeModifier(new FieldValueTypeModifier());

		context.configOverride(FieldValue.class)
			.setInclude(JsonInclude.Value.empty().withValueInclusion(JsonInclude.Include.NON_ABSENT));
	}

}
