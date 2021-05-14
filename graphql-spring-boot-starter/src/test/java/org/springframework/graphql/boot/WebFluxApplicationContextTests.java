/*
 * Copyright 2002-2021 the original author or authors.
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
import java.util.function.Consumer;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

class WebFluxApplicationContextTests {

	private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
			HttpHandlerAutoConfiguration.class, WebFluxAutoConfiguration.class,
			CodecsAutoConfiguration.class, JacksonAutoConfiguration.class,
			GraphQlAutoConfiguration.class, GraphQlServiceAutoConfiguration.class,
			WebFluxGraphQlAutoConfiguration.class);

	private static final String BASE_URL = "https://spring.example.org/graphql";


	@Test
	void query() {
		testWithWebClient(client -> {
			String query = "{" +
					"  bookById(id: \\\"book-1\\\"){ " +
					"    id" +
					"    name" +
					"    pageCount" +
					"    author" +
					"  }" +
					"}";

			client.post().uri("")
					.bodyValue("{  \"query\": \"" + query + "\"}")
					.exchange()
					.expectStatus().isOk()
					.expectBody().jsonPath("data.bookById.name").isEqualTo("GraphQL for beginners");
		});
	}

	@Test
	void queryMissing() {
		testWithWebClient(client -> client.post().uri("").bodyValue("{}").exchange().expectStatus().isBadRequest());
	}

	@Test
	void queryIsInvalidJson() {
		testWithWebClient(client -> client.post().uri("").bodyValue(":)").exchange().expectStatus().isBadRequest());
	}

	@Test
	void interceptedQuery() {
		testWithWebClient(client -> {
			String query = "{" +
					"  bookById(id: \\\"book-1\\\"){ " +
					"    id" +
					"    name" +
					"    pageCount" +
					"    author" +
					"  }" +
					"}";

			client.post().uri("")
					.bodyValue("{  \"query\": \"" + query + "\"}")
					.exchange()
					.expectStatus().isOk()
					.expectHeader().valueEquals("X-Custom-Header", "42");
		});
	}

	@Test
	void schemaEndpoint() {
		testWithWebClient(client -> {
			client.get().uri("/schema").accept(MediaType.ALL).exchange()
					.expectStatus().isOk()
					.expectHeader().contentType(MediaType.TEXT_PLAIN)
					.expectBody(String.class).value(Matchers.containsString("type Book"));
		});
	}

	private void testWithWebClient(Consumer<WebTestClient> consumer) {
		testWithApplicationContext(context -> {
			WebTestClient client = WebTestClient.bindToApplicationContext(context)
					.configureClient()
					.defaultHeaders(headers -> {
						headers.setContentType(MediaType.APPLICATION_JSON);
						headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
					})
					.baseUrl(BASE_URL)
					.build();
			consumer.accept(client);
		});
	}

	private void testWithApplicationContext(ContextConsumer<ApplicationContext> consumer) {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AUTO_CONFIGURATIONS)
				.withUserConfiguration(DataFetchersConfiguration.class, CustomWebInterceptor.class)
				.withPropertyValues(
						"spring.main.web-application-type=reactive",
						"spring.graphql.schema.printer.enabled=true",
						"spring.graphql.schema.location=classpath:books/schema.graphqls")
				.run(consumer);
	}


	@Configuration(proxyBeanMethods = false)
	static class DataFetchersConfiguration {

		@Bean
		public RuntimeWiringCustomizer bookDataFetcher() {
			return (runtimeWiring) ->
					runtimeWiring.type(newTypeWiring("Query")
							.dataFetcher("bookById", GraphQlDataFetchers.getBookByIdDataFetcher()));
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomWebInterceptor {

		@Bean
		public WebInterceptor customWebInterceptor() {
			return (input, next) -> next.handle(input).map(output ->
					output.transform(builder -> builder.responseHeader("X-Custom-Header", "42")));
		}
	}

}
