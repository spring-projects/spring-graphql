/*
 * Copyright 2002-2021 the original author or authors.
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
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.support.GraphQLSource;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

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
			assertThat(context).getFailure().getRootCause().hasMessage("'schemaResource' does not exist");
		});
	}

	@Test
	void shouldCreateBuilderWithSdl() {
		contextRunner
				.withPropertyValues("spring.graphql.schema-location:classpath:books/schema.graphqls")
				.run((context) -> assertThat(context).hasSingleBean(GraphQLSource.class));
	}

	@Test
	void shouldUseProgrammaticallyDefinedBuilder() {
		contextRunner
				.withPropertyValues("spring.graphql.schema-location:classpath:books/schema.graphqls")
				.withUserConfiguration(CustomGraphQLBuilderConfiguration.class)
				.run((context) -> {
					assertThat(context).hasBean("customGraphQLSourceBuilder");
					assertThat(context).hasSingleBean(GraphQLSource.Builder.class);
				});
	}

	@Test
	void shouldUseExceptionHandler() throws Exception {
		DataFetcher<String> dataFetcher = mock(DataFetcher.class);
		given(dataFetcher.get(any())).willThrow(new RuntimeException("Denied"));
		DataFetcherExceptionHandler handler = spy(SimpleDataFetcherExceptionHandler.class);
		RuntimeWiringCustomizer customizer = (builder) -> {
			builder.type(newTypeWiring("Query")
					.dataFetcher("bookById", dataFetcher));
		};
		contextRunner
				.withPropertyValues("spring.graphql.schema-location:classpath:books/schema.graphqls")
				.withBean(DataFetcherExceptionHandler.class, () -> handler)
				.withBean(RuntimeWiringCustomizer.class, () -> customizer)
				.run((context) -> {
					GraphQL graphQL = context.getBean(GraphQLSource.class).graphQL();
					String query = "{  bookById(id: \"book-1\"){     id    name    pageCount    author  }}";
					graphQL.execute(query);
					verify(handler).onException(any());
				});
	}

	@Configuration
	static class CustomGraphQLBuilderConfiguration {

		@Bean
		public GraphQLSource.Builder customGraphQLSourceBuilder() {
			return GraphQLSource.builder().schemaResource(new ClassPathResource("books/schema.graphqls"));
		}
	}

}
