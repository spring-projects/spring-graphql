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

package org.springframework.graphql.docs.execution.reactivedatafetcher;

import graphql.ExecutionResult;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GraphQlConfig {

	@Bean
	public SubscriptionOrderInstrumentation subscriptionOrderInstrumentation() {
		return new SubscriptionOrderInstrumentation();
	}

	static class SubscriptionOrderInstrumentation extends SimplePerformantInstrumentation {

		@Override
		public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters,
																InstrumentationState state) {
			// Enable option for keeping subscription results in upstream order
			parameters.getGraphQLContext().put(SubscriptionExecutionStrategy.KEEP_SUBSCRIPTION_EVENTS_ORDERED, true);
			return SimpleInstrumentationContext.noOp();
		}

	}

}
