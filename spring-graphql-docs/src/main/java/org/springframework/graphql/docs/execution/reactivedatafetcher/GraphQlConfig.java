/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
