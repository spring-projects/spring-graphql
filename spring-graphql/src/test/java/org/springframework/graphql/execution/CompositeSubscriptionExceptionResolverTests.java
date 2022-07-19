/*
 * Copyright 2002-2022 the original author or authors.
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

import java.time.Duration;
import java.util.List;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for resolving exceptions via {@link SubscriptionExceptionResolver}.
 * @author Rossen Stoyanchev
 */
public class CompositeSubscriptionExceptionResolverTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);


	@Test
	void subscriptionPublisherExceptionResolved() {
		String query = "subscription { greetings }";
		String schema = "type Subscription { greetings: String! } type Query { greeting: String! }";

		GraphQL graphQL = GraphQlSetup.schemaContent(schema)
				.subscriptionFetcher("greetings", env ->
						Flux.create(emitter -> {
							emitter.next("a");
							emitter.error(new RuntimeException("Test Exception"));
							emitter.next("b");
						}))
				.subscriptionExceptionResolvers(SubscriptionExceptionResolver.forSingleError(exception ->
						GraphqlErrorBuilder.newError()
								.message("Error: " + exception.getMessage())
								.errorType(ErrorType.BAD_REQUEST)
								.build()))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput(query).build();
		Flux<ResponseHelper> flux = Mono.fromFuture(graphQL.executeAsync(input))
				.map(ResponseHelper::forSubscription)
				.block(TIMEOUT);

		StepVerifier.create(flux)
				.consumeNextWith((helper) -> assertThat(helper.toEntity("greetings", String.class)).isEqualTo("a"))
				.consumeErrorWith((ex) -> {
					SubscriptionPublisherException theEx = (SubscriptionPublisherException) ex;
					List<GraphQLError> errors = theEx.getErrors();
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getMessage()).isEqualTo("Error: Test Exception");
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
				})
				.verify(TIMEOUT);
	}

	@Test
	void resolveExceptionWithThreadLocal() {
		String query = "subscription { greetings }";
		String schema = "type Subscription { greetings: String! } type Query { greeting: String! }";

		ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		nameThreadLocal.set("007");
		TestThreadLocalAccessor<String> accessor = new TestThreadLocalAccessor<>(nameThreadLocal);

		try {
			SubscriptionExceptionResolverAdapter resolver = SubscriptionExceptionResolver.forSingleError(exception ->
					GraphqlErrorBuilder.newError()
							.message("Error: " + exception.getMessage() + ", name=" + nameThreadLocal.get())
							.errorType(ErrorType.BAD_REQUEST)
							.build());
			resolver.setThreadLocalContextAware(true);

			GraphQL graphQL = GraphQlSetup.schemaContent(schema)
					.subscriptionFetcher("greetings", env ->
							Flux.create(emitter -> {
								emitter.next("a");
								emitter.error(new RuntimeException("Test Exception"));
							}))
					.subscriptionExceptionResolvers(resolver)
					.toGraphQl();

			ContextView view = ReactorContextManager.extractThreadLocalValues(accessor, Context.empty());
			ExecutionInput input = ExecutionInput.newExecutionInput(query).build();
			ReactorContextManager.setReactorContext(view, input.getGraphQLContext());

			Flux<ResponseHelper> flux = Mono.delay(Duration.ofMillis(10))
					.flatMap((aLong) -> Mono.fromFuture(graphQL.executeAsync(input)).map(ResponseHelper::forSubscription))
					.block(TIMEOUT);

			StepVerifier.create(flux)
					.consumeNextWith((helper) -> assertThat(helper.toEntity("greetings", String.class)).isEqualTo("a"))
					.consumeErrorWith((ex) -> {
						SubscriptionPublisherException theEx = (SubscriptionPublisherException) ex;
						List<GraphQLError> errors = theEx.getErrors();
						assertThat(errors).hasSize(1);
						assertThat(errors.get(0).getMessage()).isEqualTo("Error: Test Exception, name=007");
						assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
					})
					.verify(TIMEOUT);
		}
		finally {
			nameThreadLocal.remove();
		}
	}

}
