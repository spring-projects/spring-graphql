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

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.web.WebGraphQlHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link WebGraphQlHandlerAutoConfiguration}.
 *
 * @author Brian Clozel
 */
class WebGraphQlHandlerAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WebGraphQlHandlerAutoConfiguration.class))
			.withUserConfiguration(GraphQlSourceConfiguration.class);

	@Test
	void shouldContributeStandardBeans() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(BatchLoaderRegistry.class);
			assertThat(context).hasSingleBean(GraphQlService.class);
			assertThat(context).hasSingleBean(AnnotatedControllerConfigurer.class);
			assertThat(context).hasSingleBean(WebGraphQlHandler.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class GraphQlSourceConfiguration {

		@Bean
		GraphQlSource graphQlSource() {
			return mock(GraphQlSource.class);
		}
	}

}