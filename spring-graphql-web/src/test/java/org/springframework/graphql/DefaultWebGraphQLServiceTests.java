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
package org.springframework.graphql;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.util.ResourceUtils;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultWebGraphQLService}.
 */
public class DefaultWebGraphQLServiceTests {

	@Test
	void testInterceptorInvocation() throws Exception {

		StringBuilder sb = new StringBuilder();
		List<WebInterceptor> interceptors = Arrays.asList(
				new TestWebInterceptor(sb, 1), new TestWebInterceptor(sb, 2), new TestWebInterceptor(sb, 3));

		String query = "{" +
				"  bookById(id: \\\"book-1\\\"){ " +
				"    id" +
				"    name" +
				"    pageCount" +
				"    author" +
				"  }" +
				"}";

		ObjectMapper mapper = new ObjectMapper();
		Map body = mapper.reader().readValue("{\"query\": \"" + query + "\"}", Map.class);
		WebInput webInput = new WebInput(URI.create("/graphql"), new HttpHeaders(), body, "1");

		DefaultWebGraphQLService requestHandler = new DefaultWebGraphQLService(createGraphQL());
		requestHandler.setInterceptors(interceptors);

		WebOutput webOutput = requestHandler.execute(webInput).block();

		assertThat(sb.toString()).isEqualTo(":pre1:pre2:pre3:post3:post2:post1");
		assertThat(webOutput.isDataPresent()).isTrue();
		assertThat(webOutput.getResponseHeaders().get("MyHeader")).containsExactly("MyValue3", "MyValue2", "MyValue1");
	}


	private static GraphQL createGraphQL() throws Exception {
		RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
				.type(newTypeWiring("Query").dataFetcher("bookById", GraphQLDataFetchers.getBookByIdDataFetcher()))
				.build();

		File file = ResourceUtils.getFile("classpath:books/schema.graphqls");
		TypeDefinitionRegistry registry = new SchemaParser().parse(file);
		SchemaGenerator generator = new SchemaGenerator();
		GraphQLSchema schema = generator.makeExecutableSchema(registry, runtimeWiring);

		return GraphQL.newGraphQL(schema).build();
	}


	private static class TestWebInterceptor implements WebInterceptor {

		private final StringBuilder output;

		private final int index;

		public TestWebInterceptor(StringBuilder output, int index) {
			this.output = output;
			this.index = index;
		}

		@Override
		public Mono<ExecutionInput> preHandle(ExecutionInput executionInput, WebInput webInput) {
			this.output.append(":pre").append(this.index);
			return Mono.delay(Duration.ofMillis(50)).map(aLong -> executionInput);
		}

		@Override
		public Mono<WebOutput> postHandle(WebOutput output) {
			this.output.append(":post").append(this.index);
			return Mono.delay(Duration.ofMillis(50))
					.map(aLong -> output.transform(builder ->
							builder.responseHeader("myHeader", "MyValue" + this.index)));
		}
	}

}
