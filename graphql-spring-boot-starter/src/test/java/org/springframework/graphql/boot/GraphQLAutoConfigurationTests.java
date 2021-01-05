/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.graphql.boot;

import graphql.GraphQL;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQLAutoConfiguration}
 */
class GraphQLAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(GraphQLAutoConfiguration.class));


	@Test
	void shouldFailWhenSchemaFileIsMissing() {
		contextRunner.run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context).getFailure().getRootCause().isInstanceOf(MissingGraphQLSchemaException.class);
		});
	}

	@Test
	void shouldCreateBuilderWithSdl() {
		contextRunner
				.withPropertyValues("spring.graphql.schema-location:classpath:books/schema.graphqls")
				.run((context) -> {
					assertThat(context).hasSingleBean(GraphQL.Builder.class);
				});
	}

	@Test
	void shouldUseProgrammaticallyDefinedBuilder() {
		contextRunner
				.withPropertyValues("spring.graphql.schema-location:classpath:books/schema.graphqls")
				.withUserConfiguration(CustomGraphQLBuilderConfiguration.class)
				.run((context) -> {
					assertThat(context).hasBean("customGraphQLBuilder");
					assertThat(context).hasSingleBean(GraphQL.Builder.class);
				});
	}

	@Configuration
	static class CustomGraphQLBuilderConfiguration {

		@Bean
		public GraphQL.Builder customGraphQLBuilder() {
			GraphQLObjectType queryType = newObject()
					.name("helloWorldQuery")
					.field(newFieldDefinition()
							.type(GraphQLString)
							.name("hello"))
					.build();
			GraphQLSchema schema = GraphQLSchema.newSchema().query(queryType).build();
			return GraphQL.newGraphQL(schema);
		}
	}

}
