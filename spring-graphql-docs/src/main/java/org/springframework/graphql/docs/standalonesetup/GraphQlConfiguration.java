/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.docs.standalonesetup;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler;
import org.springframework.graphql.server.webmvc.GraphQlRequestPredicates;
import org.springframework.graphql.server.webmvc.GraphiQlHandler;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration(proxyBeanMethods = false)
public class GraphQlConfiguration {

	@Bean // <1>
	public AnnotatedControllerConfigurer controllerConfigurer() {
		return new AnnotatedControllerConfigurer();
	}

	@Bean // <2>
	public ExecutionGraphQlService executionGraphQlService(AnnotatedControllerConfigurer controllerConfigurer) {
		GraphQlSource graphQlSource = GraphQlSource.schemaResourceBuilder() // <3>
				.schemaResources(new ClassPathResource("graphql/schema.graphqls"))
				.configureTypeDefinitions(new ConnectionTypeDefinitionConfigurer())
				.configureRuntimeWiring(controllerConfigurer)
				.exceptionResolvers(List.of(controllerConfigurer.getExceptionResolver()))
				.build();
		DefaultBatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();
		DefaultExecutionGraphQlService service = new DefaultExecutionGraphQlService(graphQlSource);
		service.addDataLoaderRegistrar(batchLoaderRegistry);
		return service;
	}


	@Bean // <4>
	public RouterFunction<ServerResponse> graphQlRouterFunction(ExecutionGraphQlService graphQlService) {
		WebGraphQlHandler webGraphQlHandler = WebGraphQlHandler.builder(graphQlService).build();
		GraphQlHttpHandler graphQlHttpHandler = new GraphQlHttpHandler(webGraphQlHandler);
		RequestPredicate graphQlPredicate = GraphQlRequestPredicates.graphQlHttp("/graphql");
		GraphiQlHandler graphiQlHandler = new GraphiQlHandler("/graphql", "");
		return RouterFunctions.route() // <5>
				.route(graphQlPredicate, graphQlHttpHandler::handleRequest)
				.GET("/graphiql", graphiQlHandler::handleRequest)
				.build();
	}
}
