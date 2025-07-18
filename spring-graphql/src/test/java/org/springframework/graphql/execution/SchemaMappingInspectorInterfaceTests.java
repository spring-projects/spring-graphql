/*
 * Copyright 2020-present the original author or authors.
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

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * Tests for {@link SchemaMappingInspector} with interface types.
 *
 * @author Rossen Stoyanchev
 */
class SchemaMappingInspectorInterfaceTests extends SchemaMappingInspectorTestSupport {

	private static final String schema = """
				type Query {
					vehicles: [Vehicle!]!
				}
				interface Vehicle {
					name: String!
					price: Int!
				}
				type Car implements Vehicle {
					name: String!
					price: Int!
					engineType: String!
				}
				type Bike implements Vehicle {
					name: String!
					price: Int!
				}
				""";


	@Nested
	class UnmappedFields {

		@Test
		void reportUnmappedFields() {
			SchemaReport report = inspectSchema(schema, VehicleController.class);
			assertThatReport(report)
					.hasSkippedTypeCount(0)
					.hasUnmappedFieldCount(3)
					.containsUnmappedFields("Car", "price", "engineType")
					.containsUnmappedFields("Bike", "price");
		}

		interface Vehicle {
			String name();
		}
		record Car(String name) implements Vehicle { }
		record Bike(String name) implements Vehicle { }

		@Controller
		static class VehicleController {

			@QueryMapping
			List<Vehicle> vehicles() {
				throw new UnsupportedOperationException();
			}
		}
	}


	@Nested
	class ClassNameAndClassResolver {

		@Test
		void classNameFunction() {

			SchemaReport report = inspectSchema(schema,
					initializer -> initializer.classNameFunction(type -> type.getName() + "Impl"),
					VehicleController.class);

			assertThatReport(report)
					.hasSkippedTypeCount(0)
					.hasUnmappedFieldCount(3)
					.containsUnmappedFields("Car", "price", "engineType")
					.containsUnmappedFields("Bike", "price");
		}

		@Test
		void classNameTypeResolver() {

			SchemaReport report = inspectSchema(schema,
					initializer -> initializer.classMapping("Car", CarImpl.class),
					VehicleController.class);

			assertThatReport(report)
					.hasUnmappedFieldCount(2).containsUnmappedFields("Car", "price", "engineType")
					.hasSkippedTypeCount(1).containsSkippedTypes("Bike");
		}

		interface Vehicle {
			String name();
		}
		record CarImpl(String name) implements Vehicle { }
		record BikeImpl(String name) implements Vehicle { }

		@Controller
		static class VehicleController {

			@QueryMapping
			List<Vehicle> vehicles() {
				throw new UnsupportedOperationException();
			}
		}
	}


	@Nested // gh-961
	class UnmappedFieldsReportedOnlyOnce {

		@Test
		void reportUnmappedFields() {

			String schema = SchemaMappingInspectorInterfaceTests.schema + """
				extend type Query {
					cars: [Car]
				}
				""";

			SchemaReport report = inspectSchema(schema, VehicleController.class);
			assertThatReport(report)
					.hasSkippedTypeCount(0)
					.hasUnmappedFieldCount(2)
					.containsUnmappedFields("Car", "price", "engineType");
		}

		interface Vehicle {
			String name();
		}
		record Car(String name) implements Vehicle { }
		record Bike(String name, int price) implements Vehicle { }

		@Controller
		static class VehicleController {

			@QueryMapping
			List<Vehicle> vehicles() {
				throw new UnsupportedOperationException();
			}

			@QueryMapping
			List<Car> cars() {
				throw new UnsupportedOperationException();
			}
		}
	}


	@Nested
	class SkippedTypes {

		@Test
		void reportSkippedImplementations() {
			SchemaReport report = inspectSchema(schema, VehicleController.class);
			assertThatReport(report).hasSkippedTypeCount(2).containsSkippedTypes("Car", "Bike");
		}

		interface Vehicle {
			String name();
		}

		@Controller
		static class VehicleController {

			@QueryMapping
			List<Vehicle> vehicles() {
				throw new UnsupportedOperationException();
			}
		}
	}

}
