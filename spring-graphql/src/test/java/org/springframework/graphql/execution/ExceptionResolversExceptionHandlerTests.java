/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.time.Duration;
import java.util.Collections;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphqlErrorBuilder;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExceptionResolversExceptionHandler}.
 * @author Rossen Stoyanchev
 */
class ExceptionResolversExceptionHandlerTests {

	private final GraphQlSetup graphQlSetup =
			GraphQlSetup.schemaContent("type Query { greeting: String }")
					.queryFetcher("greeting", (env) -> {
						throw new IllegalArgumentException("Invalid greeting");
					});

	private final ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();


	@Test
	void resolveException() throws Exception {
		DataFetcherExceptionResolver resolver =
				DataFetcherExceptionResolver.forSingleError((ex, env) ->
						GraphqlErrorBuilder.newError(env)
								.message("Resolved error: " + ex.getMessage())
								.errorType(ErrorType.BAD_REQUEST).build());

		ExecutionResult result = this.graphQlSetup.exceptionResolver(resolver).toGraphQl()
				.executeAsync(this.input).get();

		ResponseHelper response = ResponseHelper.forResult(result);
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting");
		assertThat(response.error(0).errorType()).isEqualTo("BAD_REQUEST");
		assertThat(response.error(0).path()).isEqualTo("/greeting");

		String greeting = response.rawValue("greeting");
		assertThat(greeting).isNull();
	}

	@Test
	void resolveExceptionWithReactorContext() throws Exception {
		DataFetcherExceptionResolver resolver =
				(ex, env) -> Mono.deferContextual((view) -> Mono.just(Collections.singletonList(
						GraphqlErrorBuilder.newError(env)
								.message("Resolved error: " + ex.getMessage() + ", name=" + view.get("name"))
								.errorType(ErrorType.BAD_REQUEST).build())));

		this.input.getGraphQLContext().put("name", "007");

		ExecutionResult result = this.graphQlSetup.exceptionResolver(resolver).toGraphQl()
				.executeAsync(this.input).get();

		ResponseHelper response = ResponseHelper.forResult(result);
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting, name=007");
		assertThat(response.error(0).path()).isEqualTo("/greeting");
	}

	@Test
	void resolveExceptionWithThreadLocal() {
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("007");
		ContextRegistry.getInstance().registerThreadLocalAccessor(new TestThreadLocalAccessor<>(threadLocal));
		try {
			DataFetcherExceptionResolverAdapter resolver =
					DataFetcherExceptionResolver.forSingleError((ex, env) ->
							GraphqlErrorBuilder.newError(env)
									.message("Resolved error: " + ex.getMessage() + ", name=" + threadLocal.get())
									.errorType(ErrorType.BAD_REQUEST)
									.build());

			resolver.setThreadLocalContextAware(true);
			ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();
			snapshot.updateContext(this.input.getGraphQLContext());

			Mono<ExecutionResult> result = Mono.delay(Duration.ofMillis(10)).flatMap((aLong) ->
					Mono.fromFuture(this.graphQlSetup.exceptionResolver(resolver).toGraphQl().executeAsync(this.input)));

			ResponseHelper response = ResponseHelper.forResult(result);
			assertThat(response.errorCount()).isEqualTo(1);
			assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting, name=007");
			assertThat(response.error(0).path()).isEqualTo("/greeting");
		}
		finally {
			threadLocal.remove();
		}
	}

	@Test
	void unresolvedException() throws Exception {
		DataFetcherExceptionResolverAdapter resolver =
				DataFetcherExceptionResolver.forSingleError((ex, env) -> null);

		ExecutionResult result = this.graphQlSetup.exceptionResolver(resolver).toGraphQl()
				.executeAsync(this.input).get();

		ResponseHelper response = ResponseHelper.forResult(result);
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).startsWith("INTERNAL_ERROR for ");
		assertThat(response.error(0).path()).isEqualTo("/greeting");
		assertThat(response.error(0).errorType()).isEqualTo("INTERNAL_ERROR");

		String greeting = response.rawValue("greeting");
		assertThat(greeting).isNull();
	}

	@Test
	void suppressedException() throws Exception {

		ExecutionResult result = this.graphQlSetup
				.exceptionResolver((ex, env) -> Mono.just(Collections.emptyList())).toGraphQl()
				.executeAsync(input).get();

		String greeting = ResponseHelper.forResult(result).rawValue("greeting");
		assertThat(greeting).isNull();
	}

}
