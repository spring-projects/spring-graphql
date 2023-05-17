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

import graphql.schema.PropertyDataFetcher;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer;

/**
 * Unit tests for {@link ConnectionFieldTypeVisitor}.
 *
 * @author Rossen Stoyanchev
 */
public class ConnectionFieldTypeVisitorTests {


	@Test
	void paginationDataFetcher() {

		ListConnectionAdapter adapter = new ListConnectionAdapter();
		adapter.setInitialOffset(30);
		adapter.setHasNext(true);

		Mono<ExecutionGraphQlResponse> response = GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.dataFetcher("Query", "books", env -> BookSource.books())
				.connectionSupport(adapter)
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(BookSource.booksConnectionQuery(null)));

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

	@Test // gh-707
	void trivialDataFetcherIsSkipped() {

		Mono<ExecutionGraphQlResponse> response = GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.dataFetcher("Query", "books", new PropertyDataFetcher<>("books"))
				.connectionSupport(new ListConnectionAdapter())
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(BookSource.booksConnectionQuery(null)));

		ResponseHelper.forResponse(response).assertData("{\"books\":null}");
	}

	@Test // gh-707
	void nullValueTreatedAsEmptyConnection() {

		Mono<ExecutionGraphQlResponse> response = GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.dataFetcher("Query", "books", environment -> null)
				.connectionSupport(new ListConnectionAdapter())
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(BookSource.booksConnectionQuery(null)));

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


	private static class ListConnectionAdapter implements ConnectionAdapter {

		private int initialOffset = 0;

		private boolean hasNext = false;

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
			return (this.initialOffset != 0);
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

}
