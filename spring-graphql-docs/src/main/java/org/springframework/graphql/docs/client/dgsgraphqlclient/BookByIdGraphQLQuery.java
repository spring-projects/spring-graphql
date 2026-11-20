/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.dgsgraphqlclient;

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
