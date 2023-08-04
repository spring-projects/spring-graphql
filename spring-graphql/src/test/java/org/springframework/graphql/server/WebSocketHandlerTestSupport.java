/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.graphql.server;

import reactor.core.publisher.Flux;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;

public abstract class WebSocketHandlerTestSupport {

	protected static final String SUBSCRIPTION_ID = "1";

	protected static final String BOOK_QUERY;

	protected static final String BOOK_QUERY_PAYLOAD;

	static {
		BOOK_QUERY_PAYLOAD = "{\"query\": \"" +
				"  query TestQuery {" +
				"    bookById(id: \\\"1\\\"){ " +
				"      id" +
				"      name" +
				"      author {" +
				"        firstName" +
				"        lastName" +
				"      }" +
				"  }}\"}";

		BOOK_QUERY = "{" +
				"\"id\":\"" + WebSocketHandlerTestSupport.SUBSCRIPTION_ID + "\"," +
				"\"type\":\"subscribe\"," +
				"\"payload\":" + BOOK_QUERY_PAYLOAD +
				"}";
	}

	protected static final String BOOK_SUBSCRIPTION = "{" +
			"\"id\":\"" + SUBSCRIPTION_ID + "\"," +
			"\"type\":\"subscribe\"," +
			"\"payload\":{\"query\": \"" +
			"  subscription TestSubscription {" +
			"    bookSearch(author: \\\"George\\\") {" +
			"      id" +
			"      name" +
			"      author {" +
			"        firstName" +
			"        lastName" +
			"      }" +
			"  }}\"}" +
			"}";


	protected WebGraphQlHandler initHandler(WebGraphQlInterceptor... interceptors) {
		return GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", environment -> {
					Long id = Long.parseLong(environment.getArgument("id"));
					return BookSource.getBook(id);
				})
				.subscriptionFetcher("bookSearch", environment -> {
					String author = environment.getArgument("author");
					return Flux.fromIterable(BookSource.books())
							.filter(book -> book.getAuthor().getFullName().contains(author));
				})
				.interceptor(interceptors)
				.toWebGraphQlHandler();
	}

}
