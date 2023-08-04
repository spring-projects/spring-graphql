/*
 * Copyright 2002-2023 the original author or authors.
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
package org.springframework.graphql.execution;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * {@link TypeDefinitionConfigurer} that generates "Connection" types by looking
 * for fields whose type definition name ends in "Connection", considered by the
 * <a href="https://relay.dev/graphql/connections.htm">GraphQL Cursor Connections Specification</a>
 * to be a {@literal Connection Type}, and adding the required type definitions
 * if they don't already exist.
 *
 * <p>This is intended to be set on
 * {@link GraphQlSource.SchemaResourceBuilder#configureTypeDefinitions(TypeDefinitionConfigurer)
 * GraphQlSource.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public class ConnectionTypeDefinitionConfigurer implements TypeDefinitionConfigurer {

	private static final TypeName STRING_TYPE = new TypeName("String");

	private static final TypeName BOOLEAN_TYPE = new TypeName("Boolean");

	private static final TypeName PAGE_INFO_TYPE = new TypeName("PageInfo");


	@Override
	public void configure(TypeDefinitionRegistry registry) {

		Set<String> typeNames = findConnectionTypeNames(registry);

		if (!typeNames.isEmpty()) {
			registry.add(ObjectTypeDefinition.newObjectTypeDefinition()
					.name(PAGE_INFO_TYPE.getName())
					.fieldDefinition(initFieldDefinition("hasPreviousPage", new NonNullType(BOOLEAN_TYPE)))
					.fieldDefinition(initFieldDefinition("hasNextPage", new NonNullType(BOOLEAN_TYPE)))
					.fieldDefinition(initFieldDefinition("startCursor", STRING_TYPE))
					.fieldDefinition(initFieldDefinition("endCursor", STRING_TYPE))
					.build());

			typeNames.forEach(typeName -> {
				String connectionTypeName = typeName + "Connection";
				String edgeTypeName = typeName + "Edge";

				registry.add(ObjectTypeDefinition.newObjectTypeDefinition()
						.name(connectionTypeName)
						.fieldDefinition(initFieldDefinition("edges", new NonNullType(new ListType(new TypeName(edgeTypeName)))))
						.fieldDefinition(initFieldDefinition("pageInfo", new NonNullType(PAGE_INFO_TYPE)))
						.build());

				registry.add(ObjectTypeDefinition.newObjectTypeDefinition()
						.name(edgeTypeName)
						.fieldDefinition(initFieldDefinition("cursor", new NonNullType(STRING_TYPE)))
						.fieldDefinition(initFieldDefinition("node", new NonNullType(new TypeName(typeName))))
						.build());
			});
		}
	}

	private static Set<String> findConnectionTypeNames(TypeDefinitionRegistry registry) {
		return registry.types().values().stream()
				.filter(ImplementingTypeDefinition.class::isInstance)
				.flatMap(definition -> {
					ImplementingTypeDefinition<?> typeDefinition = (ImplementingTypeDefinition<?>) definition;
					return typeDefinition.getFieldDefinitions().stream()
							.map(fieldDefinition -> {
								Type<?> type = fieldDefinition.getType();
								return type instanceof NonNullType ? ((NonNullType) type).getType() : type;
							})
							.filter(TypeName.class::isInstance)
							.map(type -> ((TypeName) type).getName())
							.filter(name -> name.endsWith("Connection"))
							.filter(name -> registry.getType(name).isEmpty())
							.map(name -> name.substring(0, name.length() - "Connection".length()));
				})
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private FieldDefinition initFieldDefinition(String name, Type<?> returnType) {
		return FieldDefinition.newFieldDefinition().name(name).type(returnType).build();
	}

}
