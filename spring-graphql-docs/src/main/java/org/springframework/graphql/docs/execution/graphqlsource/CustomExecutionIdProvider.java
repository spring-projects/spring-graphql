/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.execution.graphqlsource;

import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;

class CustomExecutionIdProvider implements ExecutionIdProvider {
	@Override
	public ExecutionId provide(String query, String operationName, Object context) {
		return null;
	}
}
