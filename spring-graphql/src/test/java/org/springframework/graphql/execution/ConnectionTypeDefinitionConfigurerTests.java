/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.graphql.execution;

import java.util.List;
import java.util.function.Function;

import graphql.relay.Connection;
import graphql.relay.ConnectionCursor;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;

/**
 * Unit tests for {@link ConnectionTypeDefinitionConfigurer}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
class ConnectionTypeDefinitionConfigurerTests {

	@Test
	void connectionTypeGeneration() {
		GraphQlSetup graphQlSetup = GraphQlSetup.schemaResource(BookSource.paginationSchema);
		testConnectionTypeGeneration(graphQlSetup);
	}

	@Test
	void connectionTypeGenerationWithObjectExtension() {
		String schema = """
				type Query {
					bookById(id:ID): Book
				}
				type Book {
					id: ID
					name: String
				}
				extend type Query {
					books(first:Int, after:String): BookConnection
				}
				""";
		GraphQlSetup graphQlSetup = GraphQlSetup.schemaContent(schema);
		testConnectionTypeGeneration(graphQlSetup);
	}

	private static void testConnectionTypeGeneration(GraphQlSetup graphQlSetup) {
		List<Book> books = BookSource.books();

		DataFetcher<?> dataFetcher = environment ->
				createConnection(books, book -> new DefaultConnectionCursor("book:" + book.getId()));

		String document = BookSource.booksConnectionQuery("");

		Mono<ExecutionGraphQlResponse> response = graphQlSetup
				.typeDefinitionConfigurer(new ConnectionTypeDefinitionConfigurer())
				.dataFetcher("Query", "books", dataFetcher)
				.toGraphQlService()
				.execute(document);

		ResponseHelper.forResponse(response).assertData(
				"{\"books\":{" +
						"\"edges\":[" +
						"{\"cursor\":\"book:1\",\"node\":{\"id\":\"1\",\"name\":\"Nineteen Eighty-Four\"}}," +
						"{\"cursor\":\"book:2\",\"node\":{\"id\":\"2\",\"name\":\"The Great Gatsby\"}}," +
						"{\"cursor\":\"book:3\",\"node\":{\"id\":\"3\",\"name\":\"Catch-22\"}}," +
						"{\"cursor\":\"book:4\",\"node\":{\"id\":\"4\",\"name\":\"To The Lighthouse\"}}," +
						"{\"cursor\":\"book:5\",\"node\":{\"id\":\"5\",\"name\":\"Animal Farm\"}}," +
						"{\"cursor\":\"book:53\",\"node\":{\"id\":\"53\",\"name\":\"Breaking Bad\"}}," +
						"{\"cursor\":\"book:42\",\"node\":{\"id\":\"42\",\"name\":\"Hitchhiker's Guide to the Galaxy\"}}" +
						"]," +
						"\"pageInfo\":{" +
						"\"startCursor\":\"book:1\"," +
						"\"endCursor\":\"book:42\"," +
						"\"hasPreviousPage\":false," +
						"\"hasNextPage\":false}" +
						"}}"
		);
	}

	private static <N> Connection<N> createConnection(
			List<N> nodes, Function<N, ConnectionCursor> cursorFunction) {

		List<Edge<N>> edges = nodes.stream()
				.map(node -> (Edge<N>) new DefaultEdge<>(node, cursorFunction.apply(node)))
				.toList();

		DefaultPageInfo pageInfo = new DefaultPageInfo(
				edges.get(0).getCursor(), edges.get(edges.size() - 1).getCursor(), false, false);

		return new DefaultConnection<>(edges, pageInfo);
	}

}
