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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebMvcApplicationContextTests {

	public static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
			DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
			HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class,
			GraphQLAutoConfiguration.class, WebMvcGraphQLAutoConfiguration.class);

	@Test
	void endpointHandlesGraphQLQuery() {
		testWith(mockMvc -> {
			String query = "{" +
					"  bookById(id: \\\"book-1\\\"){ " +
					"    id" +
					"    name" +
					"    pageCount" +
					"    author" +
					"  }" +
					"}";
			mockMvc.perform(post("/graphql").content("{\"query\": \"" + query + "\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("data.bookById.name").value("GraphQL for beginners"));
		});
	}

	@Test
	void missingQuery() {
		testWith(mockMvc -> mockMvc.perform(post("/graphql").content("{}")).andExpect(status().isBadRequest()));
	}

	@Test
	void invalidJson() {
		testWith(mockMvc -> mockMvc.perform(post("/graphql").content(":)")).andExpect(status().isBadRequest()));
	}


	private void testWith(MockMvcConsumer mockMvcConsumer) {
		new WebApplicationContextRunner()
				.withConfiguration(AUTO_CONFIGURATIONS)
				.withUserConfiguration(DataFetchersConfiguration.class)
				.withPropertyValues(
						"spring.main.web-application-type=servlet",
						"spring.graphql.schema:classpath:books/schema.graphqls")
				.run((context) -> {
					MockHttpServletRequestBuilder builder = post("/graphQL")
							.contentType(MediaType.APPLICATION_JSON)
							.accept(MediaType.APPLICATION_JSON);
					MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(builder).build();
					mockMvcConsumer.accept(mockMvc);
				});
	}


	private static interface MockMvcConsumer {

		void accept(MockMvc mockMvc) throws Exception;

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
