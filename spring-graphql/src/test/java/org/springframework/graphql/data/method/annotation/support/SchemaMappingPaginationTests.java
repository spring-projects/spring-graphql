/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.data.method.annotation.support;

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionGraphQlService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.query.ScrollPositionCursorStrategy;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.graphql.data.query.WindowConnectionAdapter;
import org.springframework.stereotype.Controller;

/**
 * GraphQL paginated requests handled through {@code @SchemaMapping} methods.
 *
 * @author Rossen Stoyanchev
 * @author Oliver Drotbohm
 */
class SchemaMappingPaginationTests {


	@Test
	void forwardPagination() {

		String document = BookSource.booksConnectionQuery("first:2, after:\"O_2\"");
		Mono<ExecutionGraphQlResponse> response = graphQlService().execute(document);

		ResponseHelper.forResponse(response).assertData(
						"{\"books\":{" +
								"\"edges\":[" +
								"{\"cursor\":\"O_3\",\"node\":{\"id\":\"4\",\"name\":\"To The Lighthouse\"}}," +
								"{\"cursor\":\"O_4\",\"node\":{\"id\":\"5\",\"name\":\"Animal Farm\"}}" +
								"]," +
								"\"pageInfo\":{" +
								"\"startCursor\":\"O_3\"," +
								"\"endCursor\":\"O_4\"," +
								"\"hasPreviousPage\":true," +
								"\"hasNextPage\":false" +
								"}}}");
	}

	@Test // gh-775
	void zeroResults() {

		String document = BookSource.booksConnectionQuery("first:0, after:\"O_3\"");
		Mono<ExecutionGraphQlResponse> response = graphQlService().execute(document);

		ResponseHelper.forResponse(response).assertData("""
				{"books":{\
				"edges":[],\
				"pageInfo":{\
				"startCursor":null,\
				"endCursor":null,\
				"hasPreviousPage":false,\
				"hasNextPage":false}}}"""
		);
	}

	private TestExecutionGraphQlService graphQlService() {

		ScrollPositionCursorStrategy cursorStrategy = new ScrollPositionCursorStrategy();

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BookController.class);
		context.registerBean(CursorStrategy.class, () -> cursorStrategy);
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		GraphQlSetup setup = GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.runtimeWiring(configurer)
				.connectionSupport(new WindowConnectionAdapter(cursorStrategy));

		return setup.toGraphQlService();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BookController {

		@QueryMapping
		public Window<Book> books(ScrollSubrange subrange) {
			int offset = (int) ((OffsetScrollPosition) subrange.position().orElse(ScrollPosition.offset())).getOffset();
			offset++; // data stores treat offset as exclusive
			List<Book> books = BookSource.books().subList(offset, offset + subrange.count().orElse(5));
			return Window.from(books, OffsetScrollPosition.positionFunction(offset));
		}

	}

}
