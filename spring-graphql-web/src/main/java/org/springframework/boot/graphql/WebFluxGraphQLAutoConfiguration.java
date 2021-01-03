/*
 * Copyright 2020-2020 the original author or authors.
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
package org.springframework.boot.graphql;

import java.util.Collections;

import graphql.GraphQL;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.graphql.webflux.GraphQLHttpHandler;
import org.springframework.graphql.webflux.GraphQLWebSocketHandler;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(GraphQL.class)
@ConditionalOnBean(GraphQL.Builder.class)
@AutoConfigureAfter(GraphQLAutoConfiguration.class)
public class WebFluxGraphQLAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GraphQLHttpHandler graphQLHandler(GraphQL.Builder graphQLBuilder) {
		return new GraphQLHttpHandler(graphQLBuilder.build(), Collections.emptyList());
	}

	@Bean
	@ConditionalOnMissingBean
	public GraphQLWebSocketHandler graphQLWebSocketHandler(
			GraphQL.Builder graphQLBuilder, GraphQLProperties properties, ServerCodecConfigurer configurer) {

		return new GraphQLWebSocketHandler(
				graphQLBuilder.build(), Collections.emptyList(),
				configurer, properties.getConnectionInitTimeoutDuration()
		);
	}

	@Bean
	public RouterFunction<ServerResponse> graphQLEndpoint(
			GraphQLHttpHandler handler, GraphQLProperties properties, ResourceLoader resourceLoader) {

		String path = properties.getPath();
		Resource resource = resourceLoader.getResource("classpath:graphiql/index.html");

		return RouterFunctions.route()
				.GET(path, req -> ServerResponse.ok().bodyValue(resource))
				.POST(path, accept(MediaType.APPLICATION_JSON).and(contentType(MediaType.APPLICATION_JSON)), handler::handleQuery)
				.build();
	}

	@Bean
	public HandlerMapping graphQLWebSocketEndpoint(
			GraphQLWebSocketHandler handler, GraphQLProperties properties) {

		String path = properties.getWebSocketPath();
		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setUrlMap(Collections.singletonMap(path, handler));
		mapping.setOrder(-1); // Ahead of annotated controllers
		return mapping;
	}

}
