/*
 * Copyright 2020-2021 the original author or authors.
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

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.GraphQlSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlAutoConfiguration}
 */
class GraphQlAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(GraphQlAutoConfiguration.class));

	@Test
	void shouldFailWhenSchemaFileIsMissing() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context).getFailure().getRootCause().hasMessage("'schemaResources' should not be empty");
		});
	}

	@Test
	void shouldCreateBuilderWithSdl() {
		this.contextRunner.withPropertyValues("spring.graphql.schema.locations:classpath:books/")
				.run((context) -> assertThat(context).hasSingleBean(GraphQlSource.class));
	}

	@Test
	void shouldUseProgrammaticallyDefinedBuilder() {
		this.contextRunner.withPropertyValues("spring.graphql.schema.locations:classpath:books/")
				.withUserConfiguration(CustomGraphQlBuilderConfiguration.class).run((context) -> {
					assertThat(context).hasBean("customGraphQlSourceBuilder");
					assertThat(context).hasSingleBean(GraphQlSource.Builder.class);
				});
	}

	@Test
	void shouldScanLocationsForSchemaFiles() {
		this.contextRunner.withPropertyValues("spring.graphql.schema.locations:classpath:schema/")
				.run((context) -> {
					assertThat(context).hasSingleBean(GraphQlSource.class);
					GraphQlSource graphQlSource = context.getBean(GraphQlSource.class);
					GraphQLSchema schema = graphQlSource.schema();
					assertThat(schema.getObjectType("Movie")).isNotNull();
					assertThat(schema.getObjectType("Book")).isNotNull();
					assertThat(schema.getObjectType("Store")).isNotNull();
				});
	}

	@Test
	void shouldCreateRuntimeWiringBean() {
		this.contextRunner.withPropertyValues("spring.graphql.schema.locations:classpath:schema/")
				.withUserConfiguration(CustomCodeRegistryBuilderConfiguration.class)
				.run((context) -> {
					assertThat(context).hasSingleBean(CodeRegistryBuilderCustomizer.class);
					assertThat(context).hasSingleBean(GraphQLCodeRegistry.class);
					assertThat(context).hasSingleBean(RuntimeWiring.class);
				});
	}

	@Test
	void shouldNotCreateGraphQLCodeRegistryBeanWithoutCustomConfig() {
		this.contextRunner.withPropertyValues("spring.graphql.schema.locations:classpath:schema/")
				.run((context) -> {
					assertThat(context).doesNotHaveBean(CodeRegistryBuilderCustomizer.class);
					assertThat(context).doesNotHaveBean(GraphQLCodeRegistry.class);
					assertThat(context).hasSingleBean(RuntimeWiring.class);
				});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomGraphQlBuilderConfiguration {

		@Bean
		GraphQlSource.Builder customGraphQlSourceBuilder() {
			return GraphQlSource.builder().schemaResources(new ClassPathResource("books/schema.graphqls"));
		}

	}

	@Configuration
	static class CustomCodeRegistryBuilderConfiguration {

		@Bean
		CodeRegistryBuilderCustomizer codeRegistryBuilderCustomizer() {
			Map<String, DataFetcher<?>> map = new HashMap<>();
			map.put("bookById", GraphQlDataFetchers.getBookByIdDataFetcher());
			return builder -> builder.dataFetchers("Query", map);
		}

	}

}
