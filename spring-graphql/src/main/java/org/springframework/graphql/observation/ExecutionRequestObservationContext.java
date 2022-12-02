/*
 * Copyright 2020-2022 the original author or authors.
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

package org.springframework.graphql.observation;

import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import io.micrometer.observation.transport.RequestReplyReceiverContext;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#EXECUTION_REQUEST GraphQL requests}.
 * <p>This context also extends {@link RequestReplyReceiverContext} for propagating
 * tracing information from the {@link graphql.GraphQLContext}
 * or the {@link ExecutionInput#getExtensions() input extensions}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class ExecutionRequestObservationContext extends RequestReplyReceiverContext<ExecutionInput, ExecutionResult> {

	public ExecutionRequestObservationContext(ExecutionInput executionInput) {
		super(ExecutionRequestObservationContext::getContextValue);
		setCarrier(executionInput);
	}

	/**
	 * Read propagation field from the {@link graphql.GraphQLContext},
	 * or the {@link ExecutionInput#getExtensions() input extensions} as a fallback.
	 */
	private static String getContextValue(ExecutionInput executionInput, String key) {
		String value = executionInput.getGraphQLContext().get(key);
		if (value == null) {
			Map<String, Object> extensions = executionInput.getExtensions();
			if (extensions != null) {
				value = (String) extensions.get(key);
			}
		}
		return value;
	}

}
