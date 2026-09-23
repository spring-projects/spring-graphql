/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.data.federation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.TypeDefinition;
import graphql.parser.Parser;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * Resolves a simple entity key from a federated entity representation map.
 * <p>This resolver inspects the GraphQL type definitions for {@code @key(fields: "...")} directives and records keys
 * only when the directive declares exactly one top-level scalar field (for example {@code @key(fields: "id")}).
 *
 * <p>For matching types, {@link #getKey(Map)} returns the scalar key value from the representation.
 * For all other types (composite keys, nested keys), it returns the full representation map.
 *
 * @author James Bodkin
 * @since 2.0.5
 */
public class EntityKeyResolver {

	private final Map<String, String> keyField = new LinkedHashMap<>();

	EntityKeyResolver(TypeDefinitionRegistry registry) {
		for (TypeDefinition<?> type : registry.types().values()) {
			for (Directive directive : type.getDirectives("key")) {
				processDirective(type, directive);
			}
		}
	}

	private void processDirective(TypeDefinition<?> type, Directive directive) {
		Argument argument = directive.getArgument("fields");
		if (argument != null) {
			Object value = argument.getValue();
			if (value instanceof StringValue sv) {
				Parser parser = new Parser();
				Document document = parser.parseDocument("{" + sv.getValue() + "}");
				OperationDefinition operationDefinition = document.getDefinitionsOfType(OperationDefinition.class).get(0);
				SelectionSet selectionSet = operationDefinition.getSelectionSet();
				if (selectionSet.getSelections().size() > 1) {
					return;
				}

				Field field = selectionSet.getSelectionsOfType(Field.class).get(0);
				if (field.getSelectionSet() == null || field.getSelectionSet().getSelections().isEmpty()) {
					this.keyField.put(type.getName(), field.getName());
				}
			}
		}
	}

	/**
	 * Resolve the entity key for a federated entity representation map.
	 *
	 * <p>If the representation type has a registered simple key field, this method returns that field value.
	 * Otherwise, it returns the full representation as-is.
	 * @param representation the federated entity representation map, expected to contain {@code __typename}
	 * @return scalar key value for simple key entities, or the full representation map for complex key entities
	 */
	@SuppressWarnings("NullAway")
	public Object getKey(Map<String, Object> representation) {
		String typeName = (String) Objects.requireNonNull(representation.get("__typename"));
		if (this.keyField.containsKey(typeName)) {
			String fieldName = this.keyField.get(typeName);
			return representation.get(fieldName);
		}
		else {
			return representation;
		}
	}

}
