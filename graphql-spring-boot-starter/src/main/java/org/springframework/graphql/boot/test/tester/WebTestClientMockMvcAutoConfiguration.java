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

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for {@link WebTestClient} support with {@link MockMvc}.
 * <p>Temporary workaround for upcoming enhancement request in Spring Boot 2.6.0.
 *
 * @author Brian Clozel
 * @see <a href="https://github.com/spring-projects/spring-boot/issues/23067">Spring Boot 2.6.x issue</a>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ WebClient.class, WebTestClient.class, MockMvcWebTestClient.class})
@AutoConfigureAfter(name = "org.springframework.boot.test.autoconfigure.web.reactive.WebTestClientAutoConfiguration")
public class WebTestClientMockMvcAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(MockMvc.class)
	public WebTestClient webTestClient(MockMvc mockMvc, List<WebTestClientBuilderCustomizer> customizers) {
		WebTestClient.Builder builder = MockMvcWebTestClient.bindTo(mockMvc);
		for (WebTestClientBuilderCustomizer customizer : customizers) {
			customizer.customize(builder);
		}
		return builder.build();
	}
	
}
