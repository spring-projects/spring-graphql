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

import java.util.Collections;
import java.util.Map;

import graphql.GraphQL;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.graphql.webmvc.GraphQLHttpHandler;
import org.springframework.graphql.webmvc.GraphQLWebSocketHandler;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import static org.springframework.web.servlet.function.RequestPredicates.accept;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(GraphQL.class)
@ConditionalOnBean(GraphQL.Builder.class)
@AutoConfigureAfter(GraphQLAutoConfiguration.class)
public class WebMvcGraphQLAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GraphQLHttpHandler graphQLHandler(GraphQL.Builder graphQLBuilder) {
		return new GraphQLHttpHandler(graphQLBuilder.build(), Collections.emptyList());
	}

	@Bean
	@ConditionalOnMissingBean
	public GraphQLWebSocketHandler graphQLWebSocketHandler(
			GraphQL.Builder graphQLBuilder, GraphQLProperties properties, HttpMessageConverters converters) {

		HttpMessageConverter<?> converter = converters.getConverters().stream()
				.filter(candidate -> candidate.canRead(Map.class, MediaType.APPLICATION_JSON))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No JSON converter"));

		return new GraphQLWebSocketHandler(
				graphQLBuilder.build(), Collections.emptyList(),
				converter, properties.getConnectionInitTimeoutDuration()
		);
	}

	@Bean
	public RouterFunction<ServerResponse> graphQLQueryEndpoint(
			ResourceLoader resourceLoader, GraphQLHttpHandler handler, GraphQLProperties properties) {

		String path = properties.getPath();
		Resource resource = resourceLoader.getResource("classpath:graphiql/index.html");

		return RouterFunctions.route()
				.GET(path, req -> ServerResponse.ok().body(resource))
				.POST(path, contentType(MediaType.APPLICATION_JSON).and(accept(MediaType.APPLICATION_JSON)), handler::handle)
				.build();
	}

	@Bean
	public HandlerMapping graphQLWebSocketEndpoint(GraphQLWebSocketHandler handler, GraphQLProperties properties) {
		WebSocketHttpRequestHandler httpRequestHandler =
				new WebSocketHttpRequestHandler(handler, new DefaultHandshakeHandler());

		String path = properties.getWebSocketPath();
		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setUrlMap(Collections.singletonMap(path, httpRequestHandler));
		mapping.setOrder(-1); // Ahead of annotated controllers
		return mapping;
	}

}
