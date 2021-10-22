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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.ThreadLocalAccessor;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInterceptor;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for creating a
 * {@link WebGraphQlHandler}.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({GraphQL.class, GraphQlService.class})
@ConditionalOnMissingBean(GraphQlService.class)
@AutoConfigureAfter(GraphQlAutoConfiguration.class)
public class WebGraphQlHandlerAutoConfiguration {

	private final BatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();

	@Bean
	@ConditionalOnMissingBean
	public BatchLoaderRegistry batchLoaderRegistry() {
		return this.batchLoaderRegistry;
	}

	@Bean
	@ConditionalOnMissingBean
	public GraphQlService graphQlService(GraphQlSource graphQlSource) {
		ExecutionGraphQlService service = new ExecutionGraphQlService(graphQlSource);
		service.addDataLoaderRegistrar(this.batchLoaderRegistry);
		return service;
	}

	@Bean
	@ConditionalOnMissingBean
	public AnnotatedControllerConfigurer annotatedControllerConfigurer() {
		AnnotatedControllerConfigurer annotatedControllerConfigurer = new AnnotatedControllerConfigurer();
		annotatedControllerConfigurer.setConversionService(new DefaultFormattingConversionService());
		return annotatedControllerConfigurer;
	}

	@Bean
	@ConditionalOnMissingBean
	public WebGraphQlHandler webGraphQlHandler(GraphQlService service, ObjectProvider<WebInterceptor> interceptorsProvider,
			ObjectProvider<ThreadLocalAccessor> accessorsProvider) {
		return WebGraphQlHandler.builder(service)
				.interceptors(interceptorsProvider.orderedStream().collect(Collectors.toList()))
				.threadLocalAccessors(accessorsProvider.orderedStream().collect(Collectors.toList())).build();
	}

}
