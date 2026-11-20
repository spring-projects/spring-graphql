/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
		GraphQlHttpHandler graphQlHttpHandler = GraphQlHttpHandler.builder(webGraphQlHandler).build();
		RequestPredicate graphQlPredicate = GraphQlRequestPredicates.graphQlHttp("/graphql");
		GraphiQlHandler graphiQlHandler = new GraphiQlHandler("/graphql", "");
		return RouterFunctions.route() // <5>
				.route(graphQlPredicate, graphQlHttpHandler::handleRequest)
				.GET("/graphiql", graphiQlHandler::handleRequest)
				.build();
	}
}
