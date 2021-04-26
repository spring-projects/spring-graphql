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


import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.graphql.web.WebOutput;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
			MvcResult asyncResult = mockMvc.perform(post("/graphql").content("{\"query\": \"" + query + "\"}")).andReturn();
			mockMvc.perform(asyncDispatch(asyncResult))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
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

	@Test
	void interceptedQuery() {
		testWith(mockMvc -> {
			String query = "{" +
					"  bookById(id: \\\"book-1\\\"){ " +
					"    id" +
					"    name" +
					"    pageCount" +
					"    author" +
					"  }" +
					"}";
			MvcResult asyncResult = mockMvc.perform(post("/graphql").content("{\"query\": \"" + query + "\"}")).andReturn();
			mockMvc.perform(asyncDispatch(asyncResult))
					.andExpect(status().isOk())
					.andExpect(header().string("X-Custom-Header", "42"));
		});
	}

	private void testWith(MockMvcConsumer mockMvcConsumer) {
		new WebApplicationContextRunner()
				.withConfiguration(AUTO_CONFIGURATIONS)
				.withUserConfiguration(DataFetchersConfiguration.class, CustomWebInterceptor.class)
				.withPropertyValues(
						"spring.main.web-application-type=servlet",
						"spring.graphql.schema-location:classpath:books/schema.graphqls")
				.run((context) -> {
					MockHttpServletRequestBuilder builder = post("/graphql")
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
			return (builder) -> builder.type(newTypeWiring("Query")
					.dataFetcher("bookById", GraphQLDataFetchers.getBookByIdDataFetcher()));
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomWebInterceptor {

		@Bean
		public WebInterceptor customWebInterceptor() {
			return new WebInterceptor() {
				@Override
				public Mono<WebOutput> postHandle(WebOutput output) {
					return Mono.just(output.transform(builder ->
							builder.responseHeader("X-Custom-Header", "42")));
				}
			};
		}
	}

}
