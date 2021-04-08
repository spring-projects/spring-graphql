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
import java.util.stream.Collectors;

import graphql.GraphQL;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.graphql.DefaultGraphQLRequestHandler;
import org.springframework.graphql.GraphQLRequestHandler;
import org.springframework.graphql.WebInterceptor;
import org.springframework.graphql.webflux.GraphQLHttpHandler;
import org.springframework.graphql.webflux.GraphQLWebSocketHandler;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(GraphQL.class)
@ConditionalOnBean(GraphQL.class)
@AutoConfigureAfter(GraphQLAutoConfiguration.class)
public class WebFluxGraphQLAutoConfiguration {

	private static final Log logger = LogFactory.getLog(WebFluxGraphQLAutoConfiguration.class);


	@Bean
	@ConditionalOnMissingBean
	public GraphQLRequestHandler graphQLRequestHandler(GraphQL graphQL, ObjectProvider<WebInterceptor> interceptors) {
		DefaultGraphQLRequestHandler handler = new DefaultGraphQLRequestHandler(graphQL);
		handler.setInterceptors(interceptors.orderedStream().collect(Collectors.toList()));
		return handler;
	}

	@Bean
	@ConditionalOnMissingBean
	public GraphQLHttpHandler graphQLHandler(GraphQLRequestHandler requestHandler) {
		return new GraphQLHttpHandler(requestHandler);
	}

	@Bean
	public RouterFunction<ServerResponse> graphQLEndpoint(
			GraphQLHttpHandler handler, GraphQLProperties properties, ResourceLoader resourceLoader) {

		String path = properties.getPath();
		Resource resource = resourceLoader.getResource("classpath:graphiql/index.html");

		if (logger.isInfoEnabled()) {
			logger.info("GraphQL endpoint HTTP POST " + path);
		}

		return RouterFunctions.route()
				.GET(path, req -> ServerResponse.ok().bodyValue(resource))
				.POST(path, accept(MediaType.APPLICATION_JSON).and(contentType(MediaType.APPLICATION_JSON)), handler::handleQuery)
				.build();
	}

	@ConditionalOnProperty(prefix = "spring.graphql.websocket", name = "path")
	static class WebSocketConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public GraphQLWebSocketHandler graphQLWebSocketHandler(
				GraphQLRequestHandler handler, GraphQLProperties properties, ServerCodecConfigurer configurer) {

			return new GraphQLWebSocketHandler(
					handler, configurer, properties.getWebsocket().getConnectionInitTimeout());
		}

		@Bean
		public HandlerMapping graphQLWebSocketEndpoint(
				GraphQLWebSocketHandler handler, GraphQLProperties properties) {

			String path = properties.getWebsocket().getPath();
			if (logger.isInfoEnabled()) {
				logger.info("GraphQL endpoint WebSocket " + path);
			}
			WebSocketHandlerMapping handlerMapping = new WebSocketHandlerMapping();
			handlerMapping.setUrlMap(Collections.singletonMap(path, handler));
			handlerMapping.setOrder(-2); // Ahead of HTTP endpoint ("routerFunctionMapping" bean)
			return handlerMapping;
		}
	}


	private static class WebSocketHandlerMapping extends SimpleUrlHandlerMapping {

		@Override
		public Mono<Object> getHandlerInternal(ServerWebExchange exchange) {
			return ("WebSocket".equalsIgnoreCase(exchange.getRequest().getHeaders().getUpgrade()) ?
					super.getHandlerInternal(exchange) : Mono.empty());
		}
	}

}
