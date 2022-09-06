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

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.context.ContextSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for {@code @BatchMapping} methods with a {@link Principal} argument.
 *
 * @author Rossen Stoyanchev
 */
public class BatchMappingPrincipalMethodArgumentResolverTests extends BatchMappingTestSupport {

	private final Authentication authentication = new TestingAuthenticationToken(new Object(), new Object());

	private final Function<Context, Context> reactiveContextWriter = context ->
			ReactiveSecurityContextHolder.withAuthentication(this.authentication);

	private final Function<Context, Context> threadLocalContextWriter = context ->
			ContextSnapshot.captureAll().updateContext(context);


	private static Stream<Arguments> controllers() {
		return Stream.of(
				arguments(named("Returning Mono<Map<K,V>>", new BatchMonoMapController())),
				arguments(named("Returning Map<K,V>", new BatchMapController())),
				arguments(named("Returning Flux<V>", new BatchFluxController())),
				arguments(named("Returning List<V>", new BatchListController()))
		);
	}

	@ParameterizedTest
	@MethodSource("controllers")
	void resolveFromReactiveContext(PrincipalCourseController courseController) {
		testBatchLoading(courseController, this.reactiveContextWriter);
	}

	@ParameterizedTest
	@MethodSource("controllers")
	void resolveFromThreadLocalContext(PrincipalCourseController courseController) {
		SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
		try {
			testBatchLoading(courseController, this.threadLocalContextWriter);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void testBatchLoading(PrincipalCourseController controller, Function<Context, Context> contextWriter) {
		Mono<ExecutionGraphQlResponse> responseMono = Mono.delay(Duration.ofMillis(10))
				.flatMap(aLong -> {
					String document = "{ courses { id instructor { id } } }";
					return createGraphQlService(controller).execute(TestExecutionRequest.forDocument(document));
				})
				.contextWrite(contextWriter);

		List<Course> actualCourses = ResponseHelper.forResponse(responseMono).toList("courses", Course.class);
		List<Course> courses = Course.allCourses();
		assertThat(actualCourses).hasSize(courses.size());
		for (int i = 0; i < courses.size(); i++) {
			assertThat(actualCourses.get(i).instructor()).isEqualTo(courses.get(i).instructor());
		}

		assertThat(controller.principal()).isSameAs(this.authentication);
	}


	@SuppressWarnings("unused")
	private static class PrincipalCourseController extends CourseController {

		@Nullable
		protected Principal principal;

		@Nullable
		public Principal principal() {
			return this.principal;
		}

		protected void principal(Principal principal) {
			this.principal = principal;
		}

	}


	@Controller
	@SuppressWarnings("unused")
	private static class BatchMonoMapController extends PrincipalCourseController {

		@BatchMapping
		public Mono<Map<Course, Person>> instructor(List<Course> courses, Principal principal) {
			principal(principal);
			return Flux.fromIterable(courses).collect(Collectors.toMap(Function.identity(), Course::instructor));
		}

	}


	@Controller
	@SuppressWarnings("unused")
	private static class BatchMapController extends PrincipalCourseController {

		@BatchMapping
		public Map<Course, Person> instructor(List<Course> courses, Principal principal) {
			principal(principal);
			return courses.stream().collect(Collectors.toMap(Function.identity(), Course::instructor));
		}

	}

	@Controller
	@SuppressWarnings("unused")
	private static class BatchFluxController extends PrincipalCourseController {

		@BatchMapping
		public Flux<Person> instructor(List<Course> courses, Principal principal) {
			principal(principal);
			return Flux.fromIterable(courses).map(Course::instructor);
		}

	}

	@Controller
	@SuppressWarnings("unused")
	private static class BatchListController extends PrincipalCourseController {

		@BatchMapping
		public List<Person> instructor(List<Course> courses, Principal principal) {
			principal(principal);
			return courses.stream().map(Course::instructor).collect(Collectors.toList());
		}

	}

}
