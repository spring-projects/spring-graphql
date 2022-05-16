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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Test GraphQL requests handled through {@code @BatchMapping} methods.
 *
 * @author Rossen Stoyanchev
 */
@SuppressWarnings("unused")
public class BatchMappingInvocationTests extends BatchMappingTestSupport {

	private static Stream<Arguments> controllers() {
		return Stream.of(
				arguments(named("Returning Mono<Map<K,V>>", new BatchMonoMapController())),
				arguments(named("Returning Map<K,V>", new BatchMapController())),
				arguments(named("Returning Flux<V>", new BatchFluxController())),
				arguments(named("Returning List<V>", new BatchListController())),
				arguments(named("Returning Callable<Map<K,V>>", new BatchCallableMapController()))
		);
	}

	@ParameterizedTest
	@MethodSource("controllers")
	void oneToOne(CourseController controller) {
		String query = "{ " +
				"  courses { " +
				"    id" +
				"    name" +
				"    instructor {" +
				"      id" +
				"      firstName" +
				"      lastName" +
				"    }" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = createGraphQlService(controller)
				.execute(TestExecutionRequest.forDocument(query));

		List<Course> actualCourses = ResponseHelper.forResponse(responseMono).toList("courses", Course.class);
		List<Course> courses = Course.allCourses();
		assertThat(actualCourses).hasSize(courses.size());

		for (int i = 0; i < courses.size(); i++) {
			Course actualCourse = actualCourses.get(i);
			Course course = courses.get(i);
			assertThat(actualCourse).isEqualTo(course);

			Person actualInstructor = actualCourse.instructor();
			assertThat(actualInstructor.firstName()).isEqualTo(course.instructor().firstName());
			assertThat(actualInstructor.lastName()).isEqualTo(course.instructor().lastName());
		}
	}

	@ParameterizedTest
	@MethodSource("controllers")
	void oneToMany(CourseController controller) {
		String document = "{ " +
				"  courses { " +
				"    id" +
				"    name" +
				"    students {" +
				"      id" +
				"      firstName" +
				"      lastName" +
				"    }" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = createGraphQlService(controller)
				.execute(TestExecutionRequest.forDocument(document));

		List<Course> actualCourses = ResponseHelper.forResponse(responseMono).toList("courses", Course.class);
		List<Course> courses = Course.allCourses();
		assertThat(actualCourses).hasSize(courses.size());

		for (int i = 0; i < courses.size(); i++) {
			Course actualCourse = actualCourses.get(i);
			Course course = courses.get(i);
			assertThat(actualCourse.name()).isEqualTo(course.name());

			List<Person> actualStudents = actualCourse.students();
			List<Person> students = course.students();
			assertThat(actualStudents).hasSize(students.size());

			for (int j = 0; j < actualStudents.size(); j++) {
				assertThat(actualStudents.get(i).firstName()).isEqualTo(students.get(i).firstName());
				assertThat(actualStudents.get(i).lastName()).isEqualTo(students.get(i).lastName());
			}
		}
	}


	@Controller
	private static class BatchMonoMapController extends CourseController {

		@BatchMapping
		public Mono<Map<Course, Person>> instructor(List<Course> courses) {
			return Flux.fromIterable(courses).collect(Collectors.toMap(Function.identity(), Course::instructor));
		}

		@BatchMapping
		public Mono<Map<Course, List<Person>>> students(Set<Course> courses) {
			return Flux.fromIterable(courses).collect(Collectors.toMap(Function.identity(), Course::students));
		}
	}


	@Controller
	private static class BatchMapController extends CourseController {

		@BatchMapping
		public Map<Course, Person> instructor(List<Course> courses) {
			return courses.stream().collect(Collectors.toMap(Function.identity(), Course::instructor));
		}

		@BatchMapping
		public Map<Course, List<Person>> students(List<Course> courses) {
			return courses.stream().collect(Collectors.toMap(Function.identity(), Course::students));
		}

	}


	@Controller
	private static class BatchFluxController extends CourseController {

		@BatchMapping
		public Flux<Person> instructor(List<Course> courses) {
			return Flux.fromIterable(courses).map(Course::instructor);
		}

		@BatchMapping
		public Flux<List<Person>> students(List<Course> courses) {
			return Flux.fromIterable(courses).map(Course::students);
		}
	}


	@Controller
	private static class BatchListController extends CourseController {

		@BatchMapping
		public List<Person> instructor(List<Course> courses) {
			return courses.stream().map(Course::instructor).collect(Collectors.toList());
		}

		@BatchMapping
		public List<List<Person>> students(List<Course> courses) {
			return courses.stream().map(Course::students).collect(Collectors.toList());
		}
	}


	@Controller
	private static class BatchCallableMapController extends CourseController {

		@BatchMapping
		public Callable<Map<Course, Person>> instructor(List<Course> courses) {
			return () -> courses.stream().collect(Collectors.toMap(Function.identity(), Course::instructor));
		}

		@BatchMapping
		public Callable<Map<Course, List<Person>>> students(List<Course> courses) {
			return () -> courses.stream().collect(Collectors.toMap(Function.identity(), Course::students));
		}

	}


}
