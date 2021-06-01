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
import java.util.stream.Collectors;

import javax.websocket.server.ServerContainer;

import graphql.GraphQL;
import graphql.schema.idl.SchemaPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.ThreadLocalAccessor;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.graphql.web.webmvc.GraphQlHttpHandler;
import org.springframework.graphql.web.webmvc.GraphQlWebSocketHandler;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for enabling Spring GraphQL over
 * Spring MVC.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(GraphQL.class)
@ConditionalOnBean(GraphQlSource.class)
@AutoConfigureAfter(GraphQlAutoConfiguration.class)
public class WebMvcGraphQlAutoConfiguration {

	private static final Log logger = LogFactory.getLog(WebMvcGraphQlAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public WebGraphQlHandler webGraphQlHandler(ObjectProvider<WebInterceptor> interceptorsProvider,
			GraphQlService service, ObjectProvider<ThreadLocalAccessor> accessorsProvider) {

		return WebGraphQlHandler.builder(service)
				.interceptors(interceptorsProvider.orderedStream().collect(Collectors.toList()))
				.threadLocalAccessors(accessorsProvider.orderedStream().collect(Collectors.toList())).build();
	}

	@Bean
	@ConditionalOnMissingBean
	public GraphQlHttpHandler graphQlHttpHandler(WebGraphQlHandler webGraphQlHandler) {
		return new GraphQlHttpHandler(webGraphQlHandler);
	}

	@Bean
	public RouterFunction<ServerResponse> graphQlRouterFunction(GraphQlHttpHandler handler, GraphQlSource graphQlSource,
			GraphQlProperties properties, ResourceLoader resourceLoader) {

		String path = properties.getPath();
		Resource resource = resourceLoader.getResource("classpath:graphiql/index.html");
		if (logger.isInfoEnabled()) {
			logger.info("GraphQL endpoint HTTP POST " + path);
		}
		RouterFunctions.Builder builder = RouterFunctions.route().GET(path, (req) -> ServerResponse.ok().body(resource))
				.POST(path, RequestPredicates.contentType(MediaType.APPLICATION_JSON)
						.and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), handler::handleRequest);
		if (properties.getSchema().getPrinter().isEnabled()) {
			SchemaPrinter printer = new SchemaPrinter();
			builder = builder.GET(path + properties.getSchema().getPrinter().getPath(), (req) -> ServerResponse.ok()
					.contentType(MediaType.TEXT_PLAIN).body(printer.print(graphQlSource.schema())));
		}
		return builder.build();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ ServerContainer.class, WebSocketHandler.class })
	@ConditionalOnProperty(prefix = "spring.graphql.websocket", name = "path")
	public static class WebSocketConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public GraphQlWebSocketHandler graphQlWebSocketHandler(WebGraphQlHandler webGraphQlHandler,
				GraphQlProperties properties, HttpMessageConverters converters) {

			HttpMessageConverter<?> converter = converters.getConverters().stream()
					.filter((candidate) -> candidate.canRead(Map.class, MediaType.APPLICATION_JSON)).findFirst()
					.orElseThrow(() -> new IllegalStateException("No JSON converter"));

			return new GraphQlWebSocketHandler(webGraphQlHandler, converter,
					properties.getWebsocket().getConnectionInitTimeout());
		}

		@Bean
		public HandlerMapping graphQlWebSocketMapping(GraphQlWebSocketHandler handler, GraphQlProperties properties) {
			String path = properties.getWebsocket().getPath();
			if (logger.isInfoEnabled()) {
				logger.info("GraphQL endpoint WebSocket " + path);
			}
			WebSocketHandlerMapping mapping = new WebSocketHandlerMapping();
			mapping.setWebSocketUpgradeMatch(true);
			mapping.setUrlMap(Collections.singletonMap(path,
					new WebSocketHttpRequestHandler(handler, new DefaultHandshakeHandler())));
			mapping.setOrder(2); // Ahead of HTTP endpoint ("routerFunctionMapping" bean)
			return mapping;
		}

	}

}
