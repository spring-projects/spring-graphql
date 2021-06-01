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

package org.springframework.graphql.boot.test.tester;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.boot.GraphQlProperties;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link GraphQlTesterAutoConfiguration}
 *
 * @author Brian Clozel
 */
class GraphQlTesterAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(GraphQlTesterAutoConfiguration.class));

	@Test
	void shouldNotBeConfiguredWithoutGraphQlHandlerNorWebTestClient() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(GraphQlTester.class);
		});
	}

	@Test
	void shouldBeConfiguredWhenGraphQlHandlerPresent() {
		this.contextRunner.withUserConfiguration(HandlerConfiguration.class).run((context) -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(GraphQlTester.class);
		});
	}

	@Test
	void shouldBeConfiguredWhenWebTestClientPresent() {
		this.contextRunner.withUserConfiguration(WebTestClientConfiguration.class).run((context) -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(GraphQlTester.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class HandlerConfiguration {

		@Bean
		WebGraphQlHandler webGraphQlHandler() {
			return mock(WebGraphQlHandler.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class WebTestClientConfiguration {

		@Bean
		WebTestClient webTestClient() {
			RouterFunction<ServerResponse> routerFunction = RouterFunctions.route()
					.POST("/graphql", (request) -> ServerResponse.ok().build()).build();
			return WebTestClient.bindToRouterFunction(routerFunction).build();
		}

		@Bean
		GraphQlProperties graphQlProperties() {
			return new GraphQlProperties();
		}

	}

}
