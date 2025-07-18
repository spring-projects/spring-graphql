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

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import org.assertj.core.api.AbstractAssert;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;

/**
 * Base class for {@link SchemaMappingInspector} tests.
 *
 * @author Rossen Stoyanchev
 */
public class SchemaMappingInspectorTestSupport {

	protected SchemaReport inspectSchema(String schemaContent, Class<?>... controllers) {
		return inspectSchema(schemaContent, initializer -> { }, controllers);
	}

	protected SchemaReport inspectSchema(
			String schemaContent, Consumer<SchemaMappingInspector.Initializer> consumer, Class<?>... controllers) {

		GraphQLSchema schema = SchemaGenerator.createdMockedSchema(schemaContent);
		RuntimeWiring runtimeWiring = createRuntimeWiring(controllers);
		SchemaMappingInspector.Initializer initializer = SchemaMappingInspector.initializer();
		consumer.accept(initializer);
		return initializer.inspect(schema, runtimeWiring.getDataFetchers());
	}

	private RuntimeWiring createRuntimeWiring(Class<?>... controllerTypes) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		for (Class<?> controllerType : controllerTypes) {
			context.registerBean(controllerType);
		}
		context.registerBean(BatchLoaderRegistry.class, () -> new DefaultBatchLoaderRegistry());
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		configurer.configure(wiringBuilder);
		return wiringBuilder.build();
	}

	protected static SchemaInspectionReportAssert assertThatReport(SchemaReport actual) {
		return new SchemaInspectionReportAssert(actual);
	}


	protected static class SchemaInspectionReportAssert
			extends AbstractAssert<SchemaInspectionReportAssert, SchemaReport> {

		public SchemaInspectionReportAssert(SchemaReport actual) {
			super(actual, SchemaInspectionReportAssert.class);
		}

		public void isEmpty() {
			isNotNull();
			if (!this.actual.unmappedFields().isEmpty()) {
				failWithMessage("Report contains missing fields: %s", this.actual.unmappedFields());
			}
			if (!this.actual.unmappedRegistrations().isEmpty()) {
				failWithMessage("Report contains missing DataFetcher registrations for %s", this.actual.unmappedRegistrations());
			}
			if (!this.actual.skippedTypes().isEmpty()) {
				failWithMessage("Report contains skipped types: %s", this.actual.skippedTypes());
			}
		}

		public SchemaInspectionReportAssert hasUnmappedFieldCount(int expected) {
			isNotNull();
			if (this.actual.unmappedFields().size() != expected) {
				failWithMessage("Expected %s unmapped fields, found %s.", expected, this.actual.unmappedFields());
			}
			return this;
		}

		public SchemaInspectionReportAssert hasUnmappedDataFetcherCount(int expected) {
			isNotNull();
			if (this.actual.unmappedRegistrations().size() != expected) {
				failWithMessage("Expected %s unmapped fields, found %s.", expected, this.actual.unmappedFields());
			}
			return this;
		}

		public SchemaInspectionReportAssert hasUnmappedArgumentCount(int expected) {
			isNotNull();
			if (this.actual.unmappedArguments().size() != expected) {
				failWithMessage("Expected %s unmapped arguments, found %s.", expected, this.actual.unmappedArguments());
			}
			return this;
		}

		public SchemaInspectionReportAssert hasSkippedTypeCount(int expected) {
			isNotNull();
			if (this.actual.skippedTypes().size() != expected) {
				failWithMessage("Expected %s skipped types, found %s.", expected, this.actual.skippedTypes());
			}
			return this;
		}

		public SchemaInspectionReportAssert containsUnmappedFields(String typeName, String... fieldNames) {
			isNotNull();
			List<String> expected = Arrays.asList(fieldNames);
			List<String> actual = this.actual.unmappedFields().stream()
					.filter(coordinates -> coordinates.getTypeName().equals(typeName))
					.map(FieldCoordinates::getFieldName)
					.toList();
			if (!actual.containsAll(expected)) {
				failWithMessage("Expected unmapped fields for %s: %s, found %s", typeName, expected, actual);
			}
			return this;
		}

		public SchemaInspectionReportAssert containsUnmappedDataFetchers(String typeName, String... fieldNames) {
			isNotNull();
			List<FieldCoordinates> expected = Arrays.stream(fieldNames)
					.map(field -> FieldCoordinates.coordinates(typeName, field))
					.toList();
			if (!this.actual.unmappedRegistrations().keySet().containsAll(expected)) {
				failWithMessage("Expected unmapped DataFetchers for %s, found %s", expected, this.actual.unmappedRegistrations());
			}
			return this;
		}

		public SchemaInspectionReportAssert containsUnmappedArguments(String... arguments) {
			isNotNull();
			List<String> expected = Arrays.asList(arguments);
			List<String> actual = this.actual.unmappedArguments().entrySet().stream()
					.flatMap(entry -> entry.getValue().stream())
					.toList();
			if (!actual.containsAll(expected)) {
				failWithMessage("Expected unmapped arguments: %s, found %s", expected, actual);
			}
			return this;
		}

		public SchemaInspectionReportAssert containsSkippedTypes(String... fieldCoordinates) {
			isNotNull();
			List<String> expected = Arrays.asList(fieldCoordinates);
			List<String> actual = this.actual.skippedTypes().stream()
					.map(skippedType -> ((GraphQLNamedType) skippedType.type()).getName())
					.toList();
			if (!actual.containsAll(expected)) {
				failWithMessage("Expected skipped types: %s, found %s", expected, actual);
			}
			return this;
		}
	}

}
