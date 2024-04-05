/*
 * Copyright 2020-2023 the original author or authors.
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
