/*
 * Copyright 2020-2023 the original author or authors.
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

package org.springframework.graphql.data.pagination;

import java.util.Collection;
import java.util.List;

import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.SchemaTransformer;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.io.Resource;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConnectionFieldTypeVisitor}.
 *
 * @author Rossen Stoyanchev
 */
public class ConnectionFieldTypeVisitorTests {


	@Test
	void paginatedTypeIsAdapted() {

		ListConnectionAdapter adapter = new ListConnectionAdapter();
		adapter.setInitialOffset(30);
		adapter.setHasNext(true);

		Mono<ExecutionGraphQlResponse> response = GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.dataFetcher("Query", "books", env -> BookSource.books())
				.connectionSupport(adapter)
				.toGraphQlService()
				.execute(BookSource.booksConnectionQuery(null));

		ResponseHelper.forResponse(response).assertData(
				"{\"books\":{" +
						"\"edges\":[" +
						"{\"cursor\":\"O_30\",\"node\":{\"id\":\"1\",\"name\":\"Nineteen Eighty-Four\"}}," +
						"{\"cursor\":\"O_31\",\"node\":{\"id\":\"2\",\"name\":\"The Great Gatsby\"}}," +
						"{\"cursor\":\"O_32\",\"node\":{\"id\":\"3\",\"name\":\"Catch-22\"}}," +
						"{\"cursor\":\"O_33\",\"node\":{\"id\":\"4\",\"name\":\"To The Lighthouse\"}}," +
						"{\"cursor\":\"O_34\",\"node\":{\"id\":\"5\",\"name\":\"Animal Farm\"}}," +
						"{\"cursor\":\"O_35\",\"node\":{\"id\":\"53\",\"name\":\"Breaking Bad\"}}," +
						"{\"cursor\":\"O_36\",\"node\":{\"id\":\"42\",\"name\":\"Hitchhiker's Guide to the Galaxy\"}}" +
						"]," +
						"\"pageInfo\":{" +
						"\"startCursor\":\"O_30\"," +
						"\"endCursor\":\"O_36\"," +
						"\"hasPreviousPage\":true," +
						"\"hasNextPage\":true}" +
						"}}"
		);
	}

	@Test // gh-709
	void customConnectionTypeIsPassedThrough() {

		List<MyEdge<Book>> edges = BookSource.books().stream().map(book -> new MyEdge<>("0_" + book.getId(), book)).toList();
		MyPageInfo pageInfo = new MyPageInfo(edges.get(0).cursor(), edges.get(edges.size() - 1).cursor, true, true);
		MyConnection<Book> connection = new MyConnection<>(edges, pageInfo);

		Mono<ExecutionGraphQlResponse> response = GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.dataFetcher("Query", "books", env -> connection)
				.connectionSupport(new ListConnectionAdapter())
				.toGraphQlService()
				.execute(BookSource.booksConnectionQuery(null));

		ResponseHelper.forResponse(response).assertData(
				"{\"books\":{" +
						"\"edges\":[" +
						"{\"cursor\":\"0_1\",\"node\":{\"id\":\"1\",\"name\":\"Nineteen Eighty-Four\"}}," +
						"{\"cursor\":\"0_2\",\"node\":{\"id\":\"2\",\"name\":\"The Great Gatsby\"}}," +
						"{\"cursor\":\"0_3\",\"node\":{\"id\":\"3\",\"name\":\"Catch-22\"}}," +
						"{\"cursor\":\"0_4\",\"node\":{\"id\":\"4\",\"name\":\"To The Lighthouse\"}}," +
						"{\"cursor\":\"0_5\",\"node\":{\"id\":\"5\",\"name\":\"Animal Farm\"}}," +
						"{\"cursor\":\"0_53\",\"node\":{\"id\":\"53\",\"name\":\"Breaking Bad\"}}," +
						"{\"cursor\":\"0_42\",\"node\":{\"id\":\"42\",\"name\":\"Hitchhiker's Guide to the Galaxy\"}}" +
						"]," +
						"\"pageInfo\":{" +
						"\"startCursor\":\"0_1\"," +
						"\"endCursor\":\"0_42\"," +
						"\"hasPreviousPage\":true," +
						"\"hasNextPage\":true}" +
						"}}"
		);
	}

	@Test // gh-707
	void nullValueTreatedAsEmptyConnection() {

		Mono<ExecutionGraphQlResponse> response = GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.dataFetcher("Query", "books", environment -> null)
				.connectionSupport(new ListConnectionAdapter())
				.toGraphQlService()
				.execute(BookSource.booksConnectionQuery(null));

		ResponseHelper.forResponse(response).assertData(
				"{\"books\":{" +
						"\"edges\":[]," +
						"\"pageInfo\":{" +
						"\"startCursor\":null," +
						"\"endCursor\":null," +
						"\"hasPreviousPage\":false," +
						"\"hasNextPage\":false}" +
						"}}"
		);
	}


	@Nested
	class DecorationTests {

		@Test // gh-707
		void trivialDataFetcherIsNotDecorated() throws Exception {

			FieldCoordinates coordinates = FieldCoordinates.coordinates("Query", "books");
			DataFetcher<?> dataFetcher = new PropertyDataFetcher<>("books");

			DataFetcher<?> actual =
					applyConnectionFieldTypeVisitor(BookSource.paginationSchema, coordinates, dataFetcher);

			assertThat(actual).isSameAs(dataFetcher);
		}

		@Test // gh-709
		void connectionTypeWithoutEdgesIsNotDecorated() throws Exception {

			String schemaContent = """
				type Query {
					puzzles: PuzzleConnection
				}
				type PuzzleConnection {
					pageInfo: PuzzlePageInfo!
					puzzles: [PuzzleEdge]
				}
				type PuzzlePageInfo {
					total: Int!
				}
				type PuzzleEdge {
					puzzle: Puzzle
					cursor: String!
				}
				type Puzzle {
					title: String!
				}
				""";

			FieldCoordinates coordinates = FieldCoordinates.coordinates("Query", "puzzles");
			DataFetcher<?> dataFetcher = env -> null;

			DataFetcher<?> actual = applyConnectionFieldTypeVisitor(schemaContent, coordinates, dataFetcher);

			assertThat(actual).isSameAs(dataFetcher);
		}

		private static DataFetcher<?> applyConnectionFieldTypeVisitor(
				Object schemaSource, FieldCoordinates coordinates, DataFetcher<?> fetcher) throws Exception {

			TypeDefinitionRegistry registry;
			if (schemaSource instanceof Resource resource) {
				registry = new SchemaParser().parse(resource.getInputStream());
			}
			else if (schemaSource instanceof String schemaContent) {
				registry = new SchemaParser().parse(schemaContent);
			}
			else {
				throw new IllegalArgumentException();
			}

			new ConnectionTypeDefinitionConfigurer().configure(registry);

			GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(
					registry, RuntimeWiring.newRuntimeWiring()
							.type(coordinates.getTypeName(), b -> b.dataFetcher(coordinates.getFieldName(), fetcher))
							.build());

			ConnectionFieldTypeVisitor visitor =
					ConnectionFieldTypeVisitor.create(List.of(new ListConnectionAdapter()));

			schema = new SchemaTransformer().transform(schema, visitor);
			GraphQLFieldDefinition field = schema.getFieldDefinition(coordinates);
			return schema.getCodeRegistry().getDataFetcher(coordinates, field);
		}

	}



	private static class ListConnectionAdapter implements ConnectionAdapter {

		private int initialOffset;

		private boolean hasNext;

		public void setInitialOffset(int initialOffset) {
			this.initialOffset = initialOffset;
		}

		public void setHasNext(boolean hasNext) {
			this.hasNext = hasNext;
		}

		@Override
		public boolean supports(Class<?> containerType) {
			return Collection.class.isAssignableFrom(containerType);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> List<T> getContent(Object container) {
			return (List<T>) container;
		}

		@Override
		public boolean hasPrevious(Object container) {
			return this.initialOffset != 0;
		}

		@Override
		public boolean hasNext(Object container) {
			return this.hasNext;
		}

		@Override
		public String cursorAt(Object container, int index) {
			return "O_" + (this.initialOffset + index);
		}

	}


	private record MyConnection<T>(List<MyEdge<T>> edges, MyPageInfo pageInfo) {
	}

	private record MyEdge<T>(String cursor, T node) {
	}

	private record MyPageInfo(String startCursor, String endCursor, boolean hasPreviousPage, boolean hasNextPage) {
	}

}
