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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
import org.springframework.graphql.execution.MissingSchemaException;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for creating a
 * {@link GraphQlSource}.
 *
 * @author Brian Clozel
 * @author Josh Long
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({GraphQL.class, GraphQlSource.class})
@EnableConfigurationProperties(GraphQlProperties.class)
public class GraphQlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SchemaResourceResolver schemaResourceResolver(ResourcePatternResolver resourcePatternResolver, GraphQlProperties properties) {
        return new DefaultSchemaResourceResolver(resourcePatternResolver, properties.getSchema().getLocations(), properties.getSchema().getFileExtensions());
    }

    @Bean
    @ConditionalOnMissingBean
    public GraphQlSource graphQlSource(SchemaResourceResolver resolver,
                                       ResourcePatternResolver resourcePatternResolver, GraphQlProperties properties,
                                       ObjectProvider<DataFetcherExceptionResolver> exceptionResolversProvider,
                                       ObjectProvider<Instrumentation> instrumentationsProvider,
                                       ObjectProvider<GraphQlSourceBuilderCustomizer> sourceCustomizers,
                                       ObjectProvider<RuntimeWiringConfigurer> wiringConfigurers) {

        List<Resource> schemaResources = resolver.resolveSchemaResources();
        GraphQlSource.Builder builder = GraphQlSource.builder()
                .schemaResources(schemaResources.toArray(new Resource[0]))
                .exceptionResolvers(exceptionResolversProvider.orderedStream().collect(Collectors.toList()))
                .instrumentation(instrumentationsProvider.orderedStream().collect(Collectors.toList()));
        wiringConfigurers.orderedStream().forEach(builder::configureRuntimeWiring);
        sourceCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        try {
            return builder.build();
        } catch (MissingSchemaException exc) {
            throw new InvalidSchemaLocationsException(properties.getSchema().getLocations(), resourcePatternResolver, exc);
        }
    }


}

