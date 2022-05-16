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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.lang.Nullable;

/**
 * Support class for {@code @BatchMapping}, and other batch loading tests, that
 * provides a suitable schema, domain types, mock data, and configuration.
 *
 * @author Rossen Stoyanchev
 */
@SuppressWarnings("unused")
public class BatchMappingTestSupport {

	static final Map<Long, Course> courseMap = new HashMap<>();

	static final Map<Long, Person> personMap = new HashMap<>();

	static {
		Course.save(11L, "Ethical Hacking", 15L, Arrays.asList(22L, 26L, 31L));
		Course.save(19L, "Docker and Kubernetes", 17L, Arrays.asList(31L, 39L, 44L, 45L));

		Person.save(15L, "Josh", "Kelly");
		Person.save(17L, "Albert", "Murray");
		Person.save(22L, "Bonnie", "Gray");
		Person.save(26L, "John", "Perry");
		Person.save(31L, "Alaine", "Baily");
		Person.save(39L, "Jeff", "Peterson");
		Person.save(44L, "Jared", "Mccarthy");
		Person.save(45L, "Benjamin", "Brown");
	}

	static final String schema = "" +
			"type Query {" +
			"    courses: [Course]" +
			"}" +
			"type Course {" +
			"    id: ID" +
			"    name: String" +
			"    instructor: Person" +
			"    students: [Person]" +
			"}" +
			"type Person {" +
			"    id: ID" +
			"    firstName: String" +
			"    lastName: String" +
			"}";


	protected ExecutionGraphQlService createGraphQlService(CourseController controller) {
		BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.registerBean(CourseController.class, () -> controller);
		context.registerBean(BatchLoaderRegistry.class, () -> registry);
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setExecutor(new SimpleAsyncTaskExecutor());
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		return GraphQlSetup.schemaContent(schema)
				.runtimeWiring(configurer)
				.dataLoaders(registry)
				.toGraphQlService();
	}


	static class Course {

		private final Long id;

		private final String name;

		private final Long instructorId;

		private final List<Long> studentIds;

		@JsonCreator
		public Course(
				@JsonProperty("id") Long id, @JsonProperty("name") String name,
				@JsonProperty("instructor") @Nullable Person instructor,
				@JsonProperty("students") @Nullable List<Person> students) {

			this.id = id;
			this.name = name;
			this.instructorId = (instructor != null ? instructor.id() : -1);
			this.studentIds = (students != null ?
					students.stream().map(Person::id).collect(Collectors.toList()) :
					Collections.emptyList());
		}

		public Course(Long id, String name, Long instructorId, List<Long> studentIds) {
			this.id = id;
			this.name = name;
			this.instructorId = instructorId;
			this.studentIds = studentIds;
		}

		public String name() {
			return this.name;
		}

		public Long instructorId() {
			return this.instructorId;
		}

		public List<Long> studentIds() {
			return this.studentIds;
		}

		public List<Person> students() {
			return this.studentIds.stream().map(personMap::get).collect(Collectors.toList());
		}

		public Person instructor() {
			return personMap.get(this.instructorId);
		}

		public static void save(Long id, String name, Long instructorId, List<Long> studentIds) {
			Course course = new Course(id, name, instructorId, studentIds);
			courseMap.put(id, course);
		}

		public static List<Course> allCourses() {
			return new ArrayList<>(courseMap.values());
		}

		// Course is a key in the DataLoader map

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (other == null || getClass() != other.getClass()) {
				return false;
			}
			return this.id.equals(((Course) other).id);
		}

		@Override
		public int hashCode() {
			return this.id.hashCode();
		}
	}


	static class Person {

		private final Long id;

		private final String firstName;

		private final String lastName;

		@JsonCreator
		public Person(
				@JsonProperty("id") Long id,
				@JsonProperty("firstName") String firstName,
				@JsonProperty("lastName") String lastName) {

			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
		}

		public Long id() {
			return this.id;
		}

		public String firstName() {
			return this.firstName;
		}

		public String lastName() {
			return this.lastName;
		}

		public static void save(Long id, String firstName, String lastName) {
			Person person = new Person(id, firstName, lastName);
			personMap.put(id, person);
		}
	}


	static class CourseController {

		@QueryMapping
		public Collection<Course> courses() {
			return courseMap.values();
		}
	}

}
