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

import java.util.stream.Collectors;

import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.idl.RuntimeWiring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.GraphQlSource;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for creating a
 * {@link GraphQlSource}.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(GraphQL.class)
@ConditionalOnMissingBean(GraphQlSource.class)
@EnableConfigurationProperties(GraphQlProperties.class)
public class GraphQlAutoConfiguration {

	@Bean
	public GraphQlSource graphQlSource(GraphQlSource.Builder builder) {
		return builder.build();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(GraphQlSource.Builder.class)
	public static class GraphQlSourceConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public RuntimeWiring runtimeWiring(ObjectProvider<RuntimeWiringCustomizer> customizers) {
			RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
			customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
			return builder.build();
		}

		@Bean
		public GraphQlSource.Builder graphQlSourceBuilder(GraphQlProperties properties, RuntimeWiring runtimeWiring,
				ObjectProvider<DataFetcherExceptionResolver> exceptionResolversProvider, ResourceLoader resourceLoader,
				ObjectProvider<Instrumentation> instrumentationsProvider) {

			String schemaLocation = properties.getSchema().getLocation();
			return GraphQlSource.builder().schemaResource(resourceLoader.getResource(schemaLocation))
					.runtimeWiring(runtimeWiring)
					.exceptionResolvers(exceptionResolversProvider.orderedStream().collect(Collectors.toList()))
					.instrumentation(instrumentationsProvider.orderedStream().collect(Collectors.toList()));
		}

	}

}
