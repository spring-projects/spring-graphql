/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.data.federation;

import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionGraphQlService;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for requests handled through {@code @EntityMapping} methods.
 *
 * @author Rossen Stoyanchev
 */
public class EntityMappingInvocationTests {

	private static final Resource federationSchema = new ClassPathResource("books/federation-schema.graphqls");

	private static final String document = """
		    query Entities($representations: [_Any!]!) {
		        _entities(representations: $representations) {
		        ...on Book {
		             id
		             author {
		                 id
		                 firstName
		                 lastName
		             }
		         }}
		     }
		     """;


	@Test
	void fetchEntities() {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("__typename", "Book", "id", "3"),
						Map.of("__typename", "Book", "id", "5")));

		ExecutionGraphQlRequest request = TestExecutionRequest.forDocumentAndVars(document, variables);
		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(request);

		ResponseHelper helper = ResponseHelper.forResponse(responseMono);

		Author author = helper.toEntity("_entities[0].author", Author.class);
		assertThat(author.getFirstName()).isEqualTo("Joseph");
		assertThat(author.getLastName()).isEqualTo("Heller");

		author = helper.toEntity("_entities[1].author", Author.class);
		assertThat(author.getFirstName()).isEqualTo("George");
		assertThat(author.getLastName()).isEqualTo("Orwell");
	}

	@Test
	void fetchEntitiesWithExceptions() {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("id", "-95"),  // RepresentationException, no "__typename"
						Map.of("__typename", "Unknown"),  // RepresentationException, no fetcher
						Map.of("__typename", "Book", "id", "-97"),  // IllegalArgumentException
						Map.of("__typename", "Book", "id", "-98"),  // IllegalStateException
						Map.of("__typename", "Book", "id", "-99"),  // null
						Map.of("__typename", "Book", "id", "3"),
						Map.of("__typename", "Book", "id", "5")));

		ExecutionGraphQlRequest request = TestExecutionRequest.forDocumentAndVars(document, variables);
		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(request);

		ResponseHelper helper = ResponseHelper.forResponse(responseMono);

		int i = 0;

		assertError(helper, i++, "BAD_REQUEST", "Missing \"__typename\" argument");
		assertError(helper, i++, "INTERNAL_ERROR", "No entity fetcher");
		assertError(helper, i++, "BAD_REQUEST", "handled");
		assertError(helper, i++, "INTERNAL_ERROR", "not handled");
		assertError(helper, i++, "INTERNAL_ERROR", "Entity fetcher returned null or completed empty");

		assertThat(helper.toEntity("_entities[" + i++ + "].author", Author.class).getLastName()).isEqualTo("Heller");
		assertThat(helper.toEntity("_entities[" + i++ + "].author", Author.class).getLastName()).isEqualTo("Orwell");
	}

	private static void assertError(ResponseHelper helper, int i, String errorType, String msg) {
		String path = "_entities[" + i + "]";
		assertThat(helper.error(i).message()).isEqualTo(msg);
		assertThat(helper.error(i).errorType()).isEqualTo(errorType);
		assertThat(helper.error(i).path()).isEqualTo("/" + path);
		assertThat(helper.<Object>rawValue(path)).isNull();
	}

	private TestExecutionGraphQlService graphQlService() {
		BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(AuthorController.class);
		context.registerBean(BatchLoaderRegistry.class, () -> registry);
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		FederationSchemaFactory schemaFactory = new FederationSchemaFactory();
		schemaFactory.setApplicationContext(context);
		schemaFactory.afterPropertiesSet();

		return GraphQlSetup.schemaResource(federationSchema)
				.runtimeWiring(configurer)
				.schemaFactory(schemaFactory::createGraphQLSchema)
				.dataLoaders(registry)
				.toGraphQlService();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class AuthorController {

		@Nullable
		@EntityMapping
		public Book book(@Argument int id, Map<String, Object> map) {

			assertThat(map).hasSize(2)
					.containsEntry("__typename", Book.class.getSimpleName())
					.containsEntry("id", String.valueOf(id));

			return switch (id) {
				case -97 -> throw new IllegalArgumentException("handled");
				case -98 -> throw new IllegalStateException("not handled");
				case -99 -> null;
				default -> new Book((long) id, null, (Long) null);
			};
		}

		@BatchMapping
		public Flux<Author> author(List<Book> books) {
			return Flux.fromIterable(books).map(book -> BookSource.getBook(book.getId()).getAuthor());
		}

		@GraphQlExceptionHandler
		public GraphQLError handle(IllegalArgumentException ex, DataFetchingEnvironment env) {
			return GraphqlErrorBuilder.newError(env)
					.errorType(ErrorType.BAD_REQUEST)
					.message(ex.getMessage())
					.build();
		}
	}

}
