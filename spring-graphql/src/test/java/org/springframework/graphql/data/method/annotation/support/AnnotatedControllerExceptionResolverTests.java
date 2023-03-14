/*
 * Copyright 2002-2023 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link AnnotatedControllerExceptionResolver}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
public class AnnotatedControllerExceptionResolverTests {

	private final DataFetchingEnvironment environment =
			DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build();


	@Test
	void resolveToSingleError() {
		Exception ex = new IllegalArgumentException("Bad input");
		testResolve(ex, new TestController(), Collections.singletonList("handleToSingleError: " + ex.getMessage()));
	}

	@Test
	void resolveToList() {
		Exception ex = new IllegalAccessException("No access");
		testResolve(ex, new TestController(), Arrays.asList(
				"handleToList[1]: " + ex.getMessage(), "handleToList[2]: " + ex.getMessage()));
	}

	@Test
	void resolveToMono() {
		Exception ex = new InstantiationException("Failed to instantiate");
		testResolve(ex, new TestController(), Collections.singletonList("handleToMono: " + ex.getMessage()));
	}

	@Test
	void resolveToObject() {
		Exception ex = new ClassCastException("Wrong type");
		testResolve(ex, new TestController(), Collections.singletonList("handleToObject: " + ex.getMessage()));
	}

	@Test
	void resolveToVoid() {
		Exception ex = new ArithmeticException();
		testResolve(ex, new TestController(), Collections.emptyList());
	}

	@Test
	void resolveTypeDeclaredOnAnnotation() {
		Exception ex = new SecurityException();
		testResolve(ex, new TestController(), Collections.singletonList("handleWithTypeOnAnnotation"));
	}

	@Test
	void resolveFromRootCause() {
		Exception ex = new Exception("A", new Exception("B", new IndexOutOfBoundsException(5)));
		testResolve(ex, new TestController(), Collections.singletonList("handleRootCause: Index out of range: 5"));
	}

	@Test
	void leaveUnresolvedViaNullReturnValue() {
		Exception ex = new ClassNotFoundException("Not found");

		TestController controller = new TestController();
		AnnotatedControllerExceptionResolver resolver = exceptionResolver();
		resolver.registerController(controller.getClass());

		StepVerifier.create(resolver.resolveException(ex, this.environment, controller)).verifyComplete();
		StepVerifier.create(resolver.resolveException(ex, this.environment, controller)).verifyComplete();
	}

	@Test
	void resolveWithControllerAdvice() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(TestControllerAdvice.class);
		context.refresh();

		Exception ex = new IllegalArgumentException("Bad input");
		List<GraphQLError> actual = exceptionResolver(context).resolveException(ex, this.environment, null).block();

		assertThat(actual).hasSize(1);
		assertThat(actual.get(0).getMessage()).isEqualTo("handle: Bad input");
	}

	@Test
	void invalidReturnType() {
		assertThatIllegalStateException().isThrownBy(() ->
				exceptionResolver().registerController(InvalidReturnTypeController.class));
	}

	private void testResolve(Throwable ex, TestController controller, List<String> expected) {

		AnnotatedControllerExceptionResolver resolver = exceptionResolver();
		resolver.registerController(controller.getClass());

		List<GraphQLError> actual = resolver.resolveException(ex, this.environment, controller).block();

		assertThat(actual).hasSize(expected.size());
		for (int i = 0; i < expected.size(); i++) {
			assertThat(actual.get(i).getMessage()).isEqualTo(expected.get(i));
		}
	}

	private AnnotatedControllerExceptionResolver exceptionResolver() {
		return exceptionResolver(new StaticApplicationContext());
	}

	private AnnotatedControllerExceptionResolver exceptionResolver(ApplicationContext applicationContext) {
		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(applicationContext);
		configurer.afterPropertiesSet();

		HandlerMethodArgumentResolverComposite resolvers = configurer.getArgumentResolvers();
		AnnotatedControllerExceptionResolver resolver = new AnnotatedControllerExceptionResolver(resolvers);
		resolver.registerControllerAdvice(applicationContext);
		return resolver;
	}


	@SuppressWarnings("unused")
	@Controller
	private static class TestController {

		@GraphQlExceptionHandler
		GraphQLError handleToSingleError(IllegalArgumentException ex) {
			return createError("handleToSingleError", ex);
		}

		@GraphQlExceptionHandler
		List<GraphQLError> handleToList(IllegalAccessException ex) {
			return Arrays.asList(createError("handleToList[1]", ex), createError("handleToList[2]", ex));
		}

		@GraphQlExceptionHandler
		Mono<GraphQLError> handleToMono(InstantiationException ex) {
			return Mono.just(createError("handleToMono", ex));
		}

		@GraphQlExceptionHandler
		Object handleToObject(ClassCastException ex) {
			return createError("handleToObject", ex);
		}

		@GraphQlExceptionHandler(ArithmeticException.class)
		public void handleToVoid() {
		}

		@Nullable
		@GraphQlExceptionHandler
		GraphQLError handleAndLeaveNotHandled(ClassNotFoundException ex) {
			return null;
		}

		@GraphQlExceptionHandler
		public GraphQLError handleRootCause(IndexOutOfBoundsException ex) {
			return createError("handleRootCause", ex);
		}

		@GraphQlExceptionHandler(SecurityException.class)
		public GraphQLError handleWithTypeOnAnnotation() {
			return createError("handleWithTypeOnAnnotation", null);
		}

		private static GraphQLError createError(String methodName, @Nullable Throwable ex) {
			return GraphQLError.newError()
					.message(methodName + (ex != null && StringUtils.hasText(ex.getMessage()) ? ": " + ex.getMessage() : ""))
					.build();
		}

	}


	@SuppressWarnings("unused")
	@ControllerAdvice
	private static class TestControllerAdvice {

		@GraphQlExceptionHandler
		GraphQLError handle(IllegalArgumentException ex) {
			return GraphQLError.newError().message("handle: " + ex.getMessage()).build();
		}

	}


	private static class InvalidReturnTypeController {

		@GraphQlExceptionHandler
		public String handle(IllegalArgumentException ex) {
			return "Handled";
		}

	}

}
