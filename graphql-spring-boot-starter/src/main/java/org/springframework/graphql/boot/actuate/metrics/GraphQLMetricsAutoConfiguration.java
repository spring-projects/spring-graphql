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

package org.springframework.graphql.boot.actuate.metrics;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for instrumentation of Spring GraphQL
 * endpoints.
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class,
		SimpleMetricsExportAutoConfiguration.class})
@ConditionalOnBean(MeterRegistry.class)
@EnableConfigurationProperties(GraphQLMetricsProperties.class)
public class GraphQLMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(GraphQLTagsProvider.class)
	public DefaultGraphQLTagsProvider graphQLTagsProvider() {
		return new DefaultGraphQLTagsProvider();
	}

	@Bean
	public GraphQLMetricsInstrumentation graphQLMetricsInstrumentation(MeterRegistry meterRegistry,
			GraphQLTagsProvider tagsProvider, GraphQLMetricsProperties properties) {
		return new GraphQLMetricsInstrumentation(meterRegistry, tagsProvider, properties.getAutotime());
	}
}
