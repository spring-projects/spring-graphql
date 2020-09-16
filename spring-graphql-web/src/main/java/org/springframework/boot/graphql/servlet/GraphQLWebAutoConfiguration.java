package org.springframework.boot.graphql.servlet;

import graphql.GraphQL;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.graphql.GraphQLAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.servlet.GraphQLHandler;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.accept;

@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(GraphQL.class)
@ConditionalOnBean(GraphQL.Builder.class)
@AutoConfigureAfter(GraphQLAutoConfiguration.class)
public class GraphQLWebAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GraphQLHandler graphQLHandler(GraphQL.Builder graphQLBuilder) {
		return new GraphQLHandler(graphQLBuilder);
	}

	@Bean
	public RouterFunction<ServerResponse> graphQLQueryEndpoint(GraphQLHandler handler) {
		return RouterFunctions.route()
				.POST("/graphql", accept(MediaType.APPLICATION_JSON), handler::handle)
				.build();
	}

}
