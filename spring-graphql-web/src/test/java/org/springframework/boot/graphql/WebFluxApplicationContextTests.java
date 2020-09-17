package org.springframework.boot.graphql;


import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

class WebFluxApplicationContextTests {

	@Test
	void endpointHandlesGraphQLQueries() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, CodecsAutoConfiguration.class,
						WebFluxAutoConfiguration.class, HttpHandlerAutoConfiguration.class,
						GraphQLAutoConfiguration.class, WebFluxGraphQLAutoConfiguration.class))
				.withUserConfiguration(DataFetchersConfiguration.class)
				.withPropertyValues("spring.main.web-application-type=reactive", "spring.graphql.schema:classpath:books/schema.graphqls").run((context) -> {
			WebTestClient client = createWebTestClient(context);

			String query = "{" +
					"  bookById(id: \\\"book-1\\\"){ " +
					"    id" +
					"    name" +
					"    pageCount" +
					"    author" +
					"  }" +
					"}";

			String body = "{" +
					"  \"query\": \"" + query + "\"" +
					"}";

			client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
					.bodyValue(body).exchange().expectStatus().isOk()
					.expectBody()
					.jsonPath("data.bookById.name").isEqualTo("GraphQL for beginners");
		});
	}

	private WebTestClient createWebTestClient(ApplicationContext context) {
		return WebTestClient.bindToApplicationContext(context).configureClient().baseUrl("https://spring.example.org")
				.build();
	}

	@Configuration(proxyBeanMethods = false)
	static class DataFetchersConfiguration {

		@Bean
		public RuntimeWiringCustomizer bookDataFetcher() {
			return (runtimeWiring) -> runtimeWiring.type(newTypeWiring("Query")
					.dataFetcher("bookById", GraphQLDataFetchers.getBookByIdDataFetcher()));
		}

	}
}
