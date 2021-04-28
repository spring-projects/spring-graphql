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
package org.springframework.graphql.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.SchemaTransformer;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReactorDataFetcherAdapter}.
 */
public class ReactorDataFetcherAdapterTests {

	@Test
	void monoDataFetcher() throws Exception {

		GraphQL graphQL = initGraphQL("type Query { greeting: String }", builder -> {
			builder.type("Query", typeBuilder -> typeBuilder.dataFetcher("greeting",
					env -> Mono.deferContextual(context -> {
						Object name = context.get("name");
						return Mono.delay(Duration.ofMillis(50)).map(aLong -> "Hello " + name);
					})));
		});

		ExecutionInput executionInput = initExecutionInput("{ greeting }", Context.of("name", "007"));
		Map<String, Object> data = graphQL.executeAsync(executionInput).get().getData();

		assertThat(data).hasSize(1).containsEntry("greeting", "Hello 007");
	}

	@Test
	void fluxDataFetcher() throws Exception {

		GraphQL graphQL = initGraphQL("type Query { greetings: [String] }", builder -> {
			builder.type("Query", typeBuilder -> typeBuilder.dataFetcher("greetings",
					env -> Mono.delay(Duration.ofMillis(50)).flatMapMany(aLong ->
							Flux.deferContextual(context -> {
								String name = context.get("name");
								return Flux.just("Hi", "Bonjour", "Hola").map(s -> s + " " + name);
							}))));
		});

		ExecutionInput executionInput = initExecutionInput("{ greetings }", Context.of("name", "007"));

		Map<String, Object> data = graphQL.executeAsync(executionInput).get().getData();
		assertThat((List<String>) data.get("greetings")).containsExactly("Hi 007", "Bonjour 007", "Hola 007");
	}

	@Test
	void fluxDataFetcherSubscription() throws Exception {

		GraphQL graphQL = initGraphQL(
				"type Query { greeting: String } type Subscription { greetings: String }", builder ->
						builder.type("Subscription", typeBuilder -> typeBuilder.dataFetcher("greetings",
								env -> Mono.delay(Duration.ofMillis(50)).flatMapMany(aLong ->
										Flux.deferContextual(context -> {
											String name = context.get("name");
											return Flux.just("Hi", "Bonjour", "Hola").map(s -> s + " " + name);
										}))
						)));

		ExecutionInput input = initExecutionInput("subscription { greetings }", Context.of("name", "007"));
		Publisher<String> publisher = graphQL.executeAsync(input).get().getData();

		List<String> actual = Flux.from(publisher)
				.cast(ExecutionResult.class)
				.map(result -> ((Map<String, ?>) result.getData()).get("greetings"))
				.cast(String.class)
				.collectList()
				.block();

		assertThat(actual).containsExactly("Hi 007", "Bonjour 007", "Hola 007");
	}


	private GraphQL initGraphQL(String schemaValue, Consumer<RuntimeWiring.Builder> consumer) throws IOException {
		Resource schemaResource = new ByteArrayResource(schemaValue.getBytes(StandardCharsets.UTF_8));
		TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaResource.getInputStream());

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		consumer.accept(wiringBuilder);

		GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiringBuilder.build());
		schema = SchemaTransformer.transformSchema(schema, ReactorDataFetcherAdapter.TYPE_VISITOR);
		return GraphQL.newGraphQL(schema).build();
	}

	private ExecutionInput initExecutionInput(String query, Context reactorContext) {
		ExecutionInput input = ExecutionInput.newExecutionInput().query(query).build();
		ReactorDataFetcherAdapter.addReactorContext(input, reactorContext);
		return input;
	}

}
