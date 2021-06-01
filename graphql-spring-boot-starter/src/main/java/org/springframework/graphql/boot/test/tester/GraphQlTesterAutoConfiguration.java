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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.boot.GraphQlProperties;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for {@link GraphQlTester} in mock environments.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ WebClient.class, WebTestClient.class, GraphQlTester.class })
@AutoConfigureAfter(value = WebTestClientMockMvcAutoConfiguration.class,
		name = "org.springframework.boot.test.autoconfigure.web.reactive.WebTestClientAutoConfiguration")
public class GraphQlTesterAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(WebTestClient.class)
	public static class WebTestClientGraphQlTesterConfiguration {

		@Bean
		public GraphQlTester clientGraphQlTester(WebTestClient webTestClient, GraphQlProperties properties) {
			WebTestClient mutatedWebTestClient = webTestClient.mutate().baseUrl(properties.getPath()).build();
			return GraphQlTester.create(mutatedWebTestClient);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(WebTestClient.class)
	@ConditionalOnBean(WebGraphQlHandler.class)
	public static class WebGraphQlHandlerGraphQlTesterConfiguration {

		@Bean
		public GraphQlTester handlerGraphQlTester(WebGraphQlHandler handler) {
			return GraphQlTester.create(handler);
		}

	}

}
