/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.graphiql.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.server.webmvc.GraphiQlHandler;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GraphiQlConfiguration {

	@Bean
	@Order(0)
	public RouterFunction<ServerResponse> graphiQlRouterFunction() {
		RouterFunctions.Builder builder = RouterFunctions.route();
		ClassPathResource graphiQlPage = new ClassPathResource("graphiql/index.html"); // <1>
		GraphiQlHandler graphiQLHandler = new GraphiQlHandler("/graphql", "", graphiQlPage); // <2>
		builder = builder.GET("/graphiql", graphiQLHandler::handleRequest); // <3>
		return builder.build(); // <4>
	}
}
