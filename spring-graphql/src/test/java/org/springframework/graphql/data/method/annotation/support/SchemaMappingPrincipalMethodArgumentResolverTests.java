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
package org.springframework.graphql.data.method.annotation.support;

import java.lang.reflect.Method;
import java.security.Principal;
import java.time.Duration;
import java.util.function.Function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.RequestInput;
import org.springframework.graphql.RequestOutput;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.ReactorContextManager;
import org.springframework.graphql.execution.SecurityContextThreadLocalAccessor;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@code @SchemaMapping} methods with a {@link Principal} argument.
 *
 * @author Rossen Stoyanchev
 */
public class SchemaMappingPrincipalMethodArgumentResolverTests {

	private final PrincipalMethodArgumentResolver resolver = new PrincipalMethodArgumentResolver();

	private final Authentication authentication = new TestingAuthenticationToken(new Object(), new Object());

	private final Function<Context, Context> reactiveContextWriter = context ->
			ReactiveSecurityContextHolder.withAuthentication(this.authentication);

	private final Function<Context, Context> threadLocalContextWriter = context ->
			ReactorContextManager.extractThreadLocalValues(new SecurityContextThreadLocalAccessor(), context);

	private final GreetingController greetingController = new GreetingController();



	@Test
	void supportsParameter() {
		Method method = ClassUtils.getMethod(SchemaMappingPrincipalMethodArgumentResolverTests.class, "handle", (Class<?>[]) null);
		assertThat(this.resolver.supportsParameter(new MethodParameter(method, 0))).isTrue();
		assertThat(this.resolver.supportsParameter(new MethodParameter(method, 1))).isTrue();
		assertThat(this.resolver.supportsParameter(new MethodParameter(method, 2))).isFalse();
	}


	@Nested
	class Query {

		@ParameterizedTest
		@ValueSource(strings = {"greetingString", "greetingMono"})
		void resolveFromReactiveContext(String field) {
			testQuery(field, reactiveContextWriter);
		}

		@ParameterizedTest
		@ValueSource(strings = {"greetingString", "greetingMono"})
		void resolveFromThreadLocalContext(String field) {
			SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
			try {
				testQuery(field, threadLocalContextWriter);
			}
			finally {
				SecurityContextHolder.clearContext();
			}
		}

		private void testQuery(String field, Function<Context, Context> contextWriter) {
			Mono<RequestOutput> resultMono = executeAsync(
					"type Query { " + field + ": String }", "{ " + field + " }", contextWriter);

			String greeting = GraphQlResponse.from(resultMono).toEntity(field, String.class);
			assertThat(greeting).isEqualTo("Hello");
			assertThat(greetingController.principal()).isSameAs(authentication);
		}

	}


	@Nested
	class Subscription {

		@Test
		void resolveFromReactiveContext() {
			testSubscription(reactiveContextWriter);
		}

		@Test
		void resolveFromThreadLocalContext() {
			SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
			try {
				testSubscription(threadLocalContextWriter);
			}
			finally {
				SecurityContextHolder.clearContext();
			}
		}

		private void testSubscription(Function<Context, Context> contextModifier) {
			String field = "greetingSubscription";

			Mono<RequestOutput> resultMono = executeAsync(
					"type Query { greeting: String } type Subscription { " + field + ": String }",
					"subscription Greeting { " + field + " }",
					contextModifier);

			Flux<String> greetingFlux = GraphQlResponse.forSubscription(resultMono)
					.map(response -> response.toEntity(field, String.class));

			StepVerifier.create(greetingFlux).expectNext("Hello", "Hi").verifyComplete();
			assertThat(greetingController.principal()).isSameAs(authentication);
		}

	}

	private Mono<RequestOutput> executeAsync(
			String schema, String op, Function<Context, Context> contextWriter) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.registerBean(GreetingController.class, () -> greetingController);
		context.refresh();

		ExecutionGraphQlService graphQlService = GraphQlSetup.schemaContent(schema)
				.runtimeWiringForAnnotatedControllers(context)
				.toGraphQlService();

		return Mono.delay(Duration.ofMillis(10))
				.flatMap(aLong -> graphQlService.execute(new RequestInput(op, null, null, null, "1")))
				.contextWrite(contextWriter);
	}


	@SuppressWarnings("unused")
	public void handle(
			Principal principal,
			Authentication authentication,
			String s) {
	}


	@Controller
	@SuppressWarnings("unused")
	private static class GreetingController {

		@Nullable
		private Principal principal;

		@Nullable
		public Principal principal() {
			return this.principal;
		}

		@QueryMapping
		String greetingString(Principal principal) {
			this.principal = principal;
			return "Hello";
		}

		@QueryMapping
		Mono<String> greetingMono(Principal principal) {
			this.principal = principal;
			return Mono.just("Hello");
		}

		@SubscriptionMapping
		Flux<String> greetingSubscription(Principal principal) {
			this.principal = principal;
			return Flux.just("Hello", "Hi");
		}

	}

}