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

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConnectionFieldTypeVisitor}.
 *
 * @author Rossen Stoyanchev
 */
public class ConnectionFieldTypeVisitorTests {


	@Test
	void dataFetcherDecoration() throws Exception {

		String schemaContent = """
			type Query {
				books: BookConnection
			}
			type Book {
				id: ID
				name: String
			}
        """;

		String document = "{ " +
				"  books { " +
				"    edges {" +
				"      cursor," +
				"      node {" +
				"        id" +
				"        name" +
				"      }" +
				"    }" +
				"    pageInfo {" +
				"      startCursor," +
				"      endCursor," +
				"      hasPreviousPage," +
				"      hasNextPage" +
				"    }" +
				"  }" +
				"}";

		TestConnectionAdapter adapter = new TestConnectionAdapter();
		adapter.setInitialOffset(30);
		adapter.setHasNext(true);

		ExecutionGraphQlResponse response = GraphQlSetup.schemaContent(schemaContent)
				.dataFetcher("Query", "books", env -> BookSource.books())
				.typeDefinitionConfigurer(new ConnectionTypeDefinitionConfigurer())
				.typeVisitor(ConnectionFieldTypeVisitor.create(List.of(adapter)))
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document))
				.block();

		assertThat(new ObjectMapper().writeValueAsString(response.getData())).isEqualTo(
				"{\"books\":{" +
						"\"edges\":[" +
						"{\"cursor\":\"T_30\",\"node\":{\"id\":\"1\",\"name\":\"Nineteen Eighty-Four\"}}," +
						"{\"cursor\":\"T_31\",\"node\":{\"id\":\"2\",\"name\":\"The Great Gatsby\"}}," +
						"{\"cursor\":\"T_32\",\"node\":{\"id\":\"3\",\"name\":\"Catch-22\"}}," +
						"{\"cursor\":\"T_33\",\"node\":{\"id\":\"4\",\"name\":\"To The Lighthouse\"}}," +
						"{\"cursor\":\"T_34\",\"node\":{\"id\":\"5\",\"name\":\"Animal Farm\"}}," +
						"{\"cursor\":\"T_35\",\"node\":{\"id\":\"53\",\"name\":\"Breaking Bad\"}}," +
						"{\"cursor\":\"T_36\",\"node\":{\"id\":\"42\",\"name\":\"Hitchhiker's Guide to the Galaxy\"}}" +
						"]," +
						"\"pageInfo\":{" +
						"\"startCursor\":\"T_30\"," +
						"\"endCursor\":\"T_36\"," +
						"\"hasPreviousPage\":true," +
						"\"hasNextPage\":true}" +
						"}}"
		);
	}


	private static class TestConnectionAdapter implements ConnectionAdapter {

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

		@Override
		public <T> Collection<T> getContent(Object container) {
			return (Collection<T>) container;
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
			return "T_" + (this.initialOffset + index);
		}

	}

}
