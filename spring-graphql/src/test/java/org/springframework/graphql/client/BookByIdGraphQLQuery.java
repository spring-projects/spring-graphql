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

package org.springframework.graphql.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import graphql.language.VariableDefinition;

public class BookByIdGraphQLQuery extends GraphQLQuery {
	public BookByIdGraphQLQuery(String id, String queryName, Set<String> fieldsSet) {
		super("query", queryName);
		if (id != null || fieldsSet.contains("id")) {
			getInput().put("id", id);
		}
	}

	public BookByIdGraphQLQuery(String id, String queryName, Set<String> fieldsSet,
								Map<String, String> variableReferences, List<VariableDefinition> variableDefinitions) {
		super("query", queryName);
		if (id != null || fieldsSet.contains("id")) {
			getInput().put("id", id);
		}
		if (variableDefinitions != null) {
			getVariableDefinitions().addAll(variableDefinitions);
		}

		if (variableReferences != null) {
			getVariableReferences().putAll(variableReferences);
		}
	}

	public BookByIdGraphQLQuery() {
		super("query");
	}

	@Override
	public String getOperationName() {
		return "bookById";
	}

	public static Builder newRequest() {
		return new Builder();
	}

	public static class Builder {
		private Set<String> fieldsSet = new HashSet<>();

		private final Map<String, String> variableReferences = new HashMap<>();

		private final List<VariableDefinition> variableDefinitions = new ArrayList<>();

		private String id;

		private String queryName;

		public BookByIdGraphQLQuery build() {
			return new BookByIdGraphQLQuery(this.id, this.queryName, this.fieldsSet, this.variableReferences, this.variableDefinitions);

		}

		public Builder id(String id) {
			this.id = id;
			this.fieldsSet.add("id");
			return this;
		}

		public Builder idReference(String variableRef) {
			this.variableReferences.put("id", variableRef);
			this.variableDefinitions.add(VariableDefinition.newVariableDefinition(variableRef, new graphql.language.TypeName("ID")).build());
			this.fieldsSet.add("id");
			return this;
		}

		public Builder queryName(String queryName) {
			this.queryName = queryName;
			return this;
		}
	}
}
