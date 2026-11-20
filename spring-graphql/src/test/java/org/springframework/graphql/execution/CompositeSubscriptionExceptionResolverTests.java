/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.time.Duration;
import java.util.List;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for resolving exceptions via {@link SubscriptionExceptionResolver}.
 * @author Rossen Stoyanchev
 */
class CompositeSubscriptionExceptionResolverTests {

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

		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("007");
		ContextRegistry.getInstance().registerThreadLocalAccessor(new TestThreadLocalAccessor<>(threadLocal));

		try {
			SubscriptionExceptionResolverAdapter resolver = SubscriptionExceptionResolver.forSingleError(exception ->
					GraphqlErrorBuilder.newError()
							.message("Error: " + exception.getMessage() + ", name=" + threadLocal.get())
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

			ExecutionInput input = ExecutionInput.newExecutionInput(query).build();
			ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();
			snapshot.updateContext(input.getGraphQLContext());

			Flux<ResponseHelper> flux = Mono.defer(() -> Mono.fromFuture(graphQL.executeAsync(input)))
					.map(ResponseHelper::forSubscription)
					.subscribeOn(Schedulers.boundedElastic()) // restore on different thread for DataFetcher
					.block(TIMEOUT)
					.subscribeOn(Schedulers.boundedElastic()); // restore on different thread for SubscriptionExceptionResolver

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
			threadLocal.remove();
		}
	}

}
