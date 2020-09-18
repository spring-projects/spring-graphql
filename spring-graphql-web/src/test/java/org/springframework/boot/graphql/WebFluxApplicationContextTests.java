package org.springframework.boot.graphql;


import java.util.Collections;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

class WebFluxApplicationContextTests {

	private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
			HttpHandlerAutoConfiguration.class, WebFluxAutoConfiguration.class,
			CodecsAutoConfiguration.class, JacksonAutoConfiguration.class,
			GraphQLAutoConfiguration.class, WebFluxGraphQLAutoConfiguration.class);


	@Test
	void endpointHandlesGraphQLQueries() {
		testWith(client -> {
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
	void missingQuery() {
		testWith(client -> client.post().uri("").bodyValue("{}").exchange().expectStatus().isBadRequest());
	}

	@Test
	void invalidJson() {
		testWith(client -> client.post().uri("").bodyValue(":)").exchange().expectStatus().isBadRequest());
	}


	private void testWith(Consumer<WebTestClient> consumer) {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AUTO_CONFIGURATIONS)
				.withUserConfiguration(DataFetchersConfiguration.class)
				.withPropertyValues(
						"spring.main.web-application-type=reactive",
						"spring.graphql.schema:classpath:books/schema.graphqls")
				.run((context) -> {
					WebTestClient client = WebTestClient.bindToApplicationContext(context)
							.configureClient()
							.defaultHeaders(headers -> {
								headers.setContentType(MediaType.APPLICATION_JSON);
								headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
							})
							.baseUrl("https://spring.example.org/graphql")
							.build();
					consumer.accept(client);
				});
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
