/*
 * Copyright 2002-2024 the original author or authors.
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
import java.util.function.Function;
import java.util.stream.Collectors;

import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionGraphQlService;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for registration of mappings with schema interfaces types.
 * @author Rossen Stoyanchev
 */
class AnnotatedControllerConfigurerInterfaceMappingTests {

	private static final String SCHEMA = """
			type Query {
				activities: [Activity!]!
			}
			interface Activity {
				id: ID!
				labels: [Label!]!
				coordinator: User!
			}
			type FooActivity implements Activity {
				id: ID!
				labels: [Label!]!
				coordinator: User!
			}
			type BarActivity implements Activity {
				id: ID!
				labels: [Label!]!
				coordinator: User!
			}
			type Label {
				name: String!
			}
			type User {
				name: String!
			}
			""";

	private final DefaultBatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();


	@Test
	void schemaMapping() {
		GraphQLCodeRegistry registry = initCodeRegistry(SchemaMappingController.class);

		assertDataFetcher(registry, "FooActivity", "labels");
		assertDataFetcher(registry, "BarActivity", "labels");
		assertDataFetcher(registry, "FooActivity", "coordinator");
		assertDataFetcher(registry, "BarActivity", "coordinator");
	}

	@Test
	void batchMapping() {
		GraphQLCodeRegistry registry = initCodeRegistry(BatchMappingController.class);

		assertDataFetcher(registry, "FooActivity", "labels");
		assertDataFetcher(registry, "BarActivity", "labels");
		assertDataFetcher(registry, "FooActivity", "coordinator");
		assertDataFetcher(registry, "BarActivity", "coordinator");
	}

	@Test
	void schemaMappingOverride() {
		testOverride(SchemaMappingOverrideController.class);
	}

	@Test
	void batchMappingOverride() {
		testOverride(BatchMappingOverrideController.class);
	}

	private void testOverride(Class<?> controllerClass) {
		String document = """
				{
					activities {
						labels {
							name
						}
						coordinator {
							name
						}
					}
				}
				""";

		TestExecutionGraphQlService service =
				initGraphQLSetup(controllerClass).dataLoaders(this.batchLoaderRegistry).toGraphQlService();

		ResponseHelper helper = ResponseHelper.forResponse(service.execute(document));
		List<Map<String, Object>> activities = helper.rawValue("activities");

		assertThat(activities).hasSize(2);

		assertThat(activities.get(0).get("labels")).isEqualTo(List.of(Map.of("name", "foo-label")));
		assertThat(activities.get(0).get("coordinator")).isEqualTo(Map.of("name", "foo-user"));

		assertThat(activities.get(1).get("labels")).isEqualTo(List.of(Map.of("name", "label")));
		assertThat(activities.get(1).get("coordinator")).isEqualTo(Map.of("name", "user"));
	}

	private GraphQLCodeRegistry initCodeRegistry(Class<?> controllerClass) {
		return initGraphQLSetup(controllerClass).toGraphQl().getGraphQLSchema().getCodeRegistry();
	}

	private GraphQlSetup initGraphQLSetup(Class<?> controllerClass) {
		AnnotationConfigApplicationContext appContext = new AnnotationConfigApplicationContext();
		appContext.registerBean(controllerClass);
		appContext.registerBean(BatchLoaderRegistry.class, () -> batchLoaderRegistry);
		appContext.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(appContext);
		configurer.afterPropertiesSet();

		return GraphQlSetup.schemaContent(SCHEMA).runtimeWiring(configurer);
	}

	private static void assertDataFetcher(GraphQLCodeRegistry registry, String typeName, String fieldName) {
		assertThat(registry.hasDataFetcher(FieldCoordinates.coordinates(typeName, fieldName))).isTrue();
	}


	private static class BaseController {

		@QueryMapping
		List<Activity> activities() {
			return List.of(new FooActivity(), new BarActivity());
		}

	}


	@SuppressWarnings("unused")
	@Controller
	private static class SchemaMappingController extends BaseController {

		@SchemaMapping
		List<Label> labels(Activity activity) {
			throw new UnsupportedOperationException();
		}

		@SchemaMapping
		User coordinator(Activity activity) {
			throw new UnsupportedOperationException();
		}
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BatchMappingController extends BaseController {

		@BatchMapping
		Map<Activity, List<Label>> labels(List<Activity> activities) {
			throw new UnsupportedOperationException();
		}

		@BatchMapping
		Map<Activity, User> coordinator(List<Activity> activities) {
			throw new UnsupportedOperationException();
		}
	}


	@SuppressWarnings("unused")
	@Controller
	private static class SchemaMappingOverrideController extends BaseController {

		@SchemaMapping
		List<Label> labels(Activity activity) {
			return List.of(new Label("label"));
		}

		@SchemaMapping
		User coordinator(Activity activity) {
			return new User("user");
		}

		@SchemaMapping
		List<Label> labels(FooActivity activity) {
			return List.of(new Label("foo-label"));
		}

		@SchemaMapping
		User coordinator(FooActivity activity) {
			return new User("foo-user");
		}
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BatchMappingOverrideController extends BaseController {

		@BatchMapping
		Map<Activity, List<Label>> labels(List<Activity> activities) {
			return activities.stream().collect(Collectors.toMap(a -> a, a -> List.of(new Label("label"))));
		}

		@BatchMapping
		Map<Activity, User> coordinator(List<Activity> activities) {
			return activities.stream().collect(Collectors.toMap(Function.identity(), a -> new User("user")));
		}

		@BatchMapping(field = "labels")
		Map<Activity, List<Label>> fooLabels(List<FooActivity> activities) {
			return activities.stream().collect(Collectors.toMap(a -> a, a -> List.of(new Label("foo-label"))));
		}

		@BatchMapping(field = "coordinator")
		Map<Activity, User> fooCoordinator(List<FooActivity> activities) {
			return activities.stream().collect(Collectors.toMap(Function.identity(), a -> new User("foo-user")));
		}
	}


	@SuppressWarnings("unused")
	private interface Activity {

		default Long getId() {
			return 1L;
		}

		default List<Label> getLabels() {
			return List.of(new Label("label"));
		}

		default User getCoordinator() {
			return new User("user");
		}
	}

	@SuppressWarnings("unused")
	private static class FooActivity implements Activity {
	}

	@SuppressWarnings("unused")
	private static class BarActivity implements Activity {
	}

	private record Label(String name) {
	}

	private record User(String name) {
	}

}
