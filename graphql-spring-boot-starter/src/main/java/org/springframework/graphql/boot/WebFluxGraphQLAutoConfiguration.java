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
import graphql.schema.idl.SchemaPrinter;
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
import org.springframework.graphql.GraphQLService;
import org.springframework.graphql.execution.GraphQLSource;
import org.springframework.graphql.web.WebGraphQLHandler;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.graphql.web.webflux.GraphQLHttpHandler;
import org.springframework.graphql.web.webflux.GraphQLWebSocketHandler;
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
@ConditionalOnBean(GraphQLSource.class)
@AutoConfigureAfter(GraphQLAutoConfiguration.class)
public class WebFluxGraphQLAutoConfiguration {

	private static final Log logger = LogFactory.getLog(WebFluxGraphQLAutoConfiguration.class);


	@Bean
	@ConditionalOnMissingBean
	public WebGraphQLHandler webGraphQLHandler(ObjectProvider<WebInterceptor> interceptors, GraphQLService service) {
		return WebInterceptor.createHandler(interceptors.orderedStream().collect(Collectors.toList()), service);
	}

	@Bean
	@ConditionalOnMissingBean
	public GraphQLHttpHandler graphQLHttpHandler(WebGraphQLHandler webGraphQLHandler) {
		return new GraphQLHttpHandler(webGraphQLHandler);
	}

	@Bean
	public RouterFunction<ServerResponse> graphQLEndpoint(GraphQLHttpHandler handler, GraphQLSource graphQLSource,
			GraphQLProperties properties, ResourceLoader resourceLoader) {

		String path = properties.getPath();
		Resource resource = resourceLoader.getResource("classpath:graphiql/index.html");
		if (logger.isInfoEnabled()) {
			logger.info("GraphQL endpoint HTTP POST " + path);
		}
		RouterFunctions.Builder builder = RouterFunctions.route()
				.GET(path, req -> ServerResponse.ok().bodyValue(resource))
				.POST(path, accept(MediaType.APPLICATION_JSON).and(contentType(MediaType.APPLICATION_JSON)), handler::handleRequest);
		if (properties.getSchema().getPrinter().isEnabled()) {
			SchemaPrinter schemaPrinter = new SchemaPrinter();
			builder = builder.GET(path + properties.getSchema().getPrinter().getPath(),
					req -> ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(schemaPrinter.print(graphQLSource.schema())));
		}
		return builder.build();
	}

	@ConditionalOnProperty(prefix = "spring.graphql.websocket", name = "path")
	static class WebSocketConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public GraphQLWebSocketHandler graphQLWebSocketHandler(
				WebGraphQLHandler webGraphQLHandler, GraphQLProperties properties, ServerCodecConfigurer configurer) {

			return new GraphQLWebSocketHandler(
					webGraphQLHandler, configurer, properties.getWebsocket().getConnectionInitTimeout());
		}

		@Bean
		public HandlerMapping graphQLWebSocketEndpoint(
				GraphQLWebSocketHandler graphQLWebSocketHandler, GraphQLProperties properties) {

			String path = properties.getWebsocket().getPath();
			if (logger.isInfoEnabled()) {
				logger.info("GraphQL endpoint WebSocket " + path);
			}
			WebSocketHandlerMapping handlerMapping = new WebSocketHandlerMapping();
			handlerMapping.setUrlMap(Collections.singletonMap(path, graphQLWebSocketHandler));
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
