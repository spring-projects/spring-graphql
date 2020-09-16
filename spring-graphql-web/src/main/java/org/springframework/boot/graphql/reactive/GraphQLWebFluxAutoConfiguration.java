package org.springframework.boot.graphql.reactive;

import graphql.GraphQL;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.graphql.GraphQLAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.WebFluxGraphQLHandler;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(GraphQL.class)
@ConditionalOnBean(GraphQL.Builder.class)
@AutoConfigureAfter(GraphQLAutoConfiguration.class)
public class GraphQLWebFluxAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WebFluxGraphQLHandler graphQLHandler(GraphQL.Builder graphQLBuilder) {
		return new WebFluxGraphQLHandler(graphQLBuilder);
	}

	@Bean
	public RouterFunction<ServerResponse> graphQLQueryEndpoint(WebFluxGraphQLHandler handler) {
		return RouterFunctions.route().POST("/graphql", handler::handle).build();
	}

}
