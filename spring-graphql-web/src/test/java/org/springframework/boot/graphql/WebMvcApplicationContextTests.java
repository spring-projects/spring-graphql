package org.springframework.boot.graphql;


import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class WebMvcApplicationContextTests {

	@Test
	void endpointHandlesGraphQLQueries() {
		new WebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
						WebMvcAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
						GraphQLAutoConfiguration.class, WebMvcGraphQLAutoConfiguration.class))
				.withUserConfiguration(DataFetchersConfiguration.class)
				.withPropertyValues("spring.main.web-application-type=servlet", "spring.graphql.schema:classpath:books/schema.graphqls").run((context) -> {

			MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
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

			mockMvc.perform(post("/graphql").content(body).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("data.bookById.name").value("GraphQL for beginners"));
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
