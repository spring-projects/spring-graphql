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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
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
@ConditionalOnClass({GraphQL.class, GraphQlSource.class})
@ConditionalOnMissingBean(GraphQlSource.class)
@EnableConfigurationProperties(GraphQlProperties.class)
public class GraphQlAutoConfiguration {

	private static final String[] SCHEMA_FILES_EXTENSIONS = new String[] {"*.graphqls", "*.graphql", "*.gql", "*.gqls"};

	@Bean
	public GraphQlSource graphQlSource(ResourcePatternResolver resourcePatternResolver, GraphQlProperties properties,
			ObjectProvider<DataFetcherExceptionResolver> exceptionResolversProvider,
			ObjectProvider<Instrumentation> instrumentationsProvider,
			ObjectProvider<GraphQlSourceBuilderCustomizer> sourceCustomizers,
			ObjectProvider<RuntimeWiringBuilderCustomizer> wiringCustomizers) throws IOException {


		List<Resource> schemaResources = resolveSchemaResources(resourcePatternResolver, properties.getSchema().getLocations());
		GraphQlSource.Builder builder = GraphQlSource.builder().schemaResources(schemaResources.toArray(new Resource[0]))
				.exceptionResolvers(exceptionResolversProvider.orderedStream().collect(Collectors.toList()))
				.instrumentation(instrumentationsProvider.orderedStream().collect(Collectors.toList()));
		wiringCustomizers.orderedStream().forEach((customizer) -> builder.runtimeWiring(customizer::customize));
		sourceCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
		return builder.build();
	}

	private List<Resource> resolveSchemaResources(ResourcePatternResolver resolver, List<String> schemaLocations) throws IOException {
		List<Resource> schemaResources = new ArrayList<>();
		for (String location : schemaLocations) {
			for (String extension : SCHEMA_FILES_EXTENSIONS) {
				schemaResources.addAll(Arrays.asList(resolver.getResources(location + extension)));
			}
		}
		return schemaResources;
	}

}
