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

package org.springframework.graphql.web;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import reactor.core.publisher.Flux;

import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;

public abstract class BookTestUtils {

	public static final String SUBSCRIPTION_ID = "1";

	public static final String BOOK_QUERY = "{" +
			"\"id\":\"" + BookTestUtils.SUBSCRIPTION_ID + "\"," +
			"\"type\":\"subscribe\"," +
			"\"payload\":{\"query\": \"" +
			"  query TestQuery {" +
			"    bookById(id: \\\"1\\\"){ " +
			"      id" + "      name" +
			"      author" + "  }}\"}" +
			"}";

	public static final String BOOK_SUBSCRIPTION = "{" +
			"\"id\":\"" + SUBSCRIPTION_ID + "\"," +
			"\"type\":\"subscribe\"," +
			"\"payload\":{\"query\": \"" +
			"  subscription TestSubscription {" +
			"    bookSearch(author: \\\"George\\\") {" +
			"      id" +
			"      name" +
			"      author" +
			"  }}\"}" +
			"}";

	private static final Map<Long, Book> booksMap = new HashMap<>(4);
	static {
		booksMap.put(1L, new Book(1L, "Nineteen Eighty-Four", "George Orwell"));
		booksMap.put(2L, new Book(2L, "The Great Gatsby", "F. Scott Fitzgerald"));
		booksMap.put(3L, new Book(3L, "Catch-22", "Joseph Heller"));
		booksMap.put(4L, new Book(4L, "To The Lighthouse", "Virginia Woolf"));
		booksMap.put(5L, new Book(5L, "Animal Farm", "George Orwell"));
	}

	public static WebGraphQlHandler initWebGraphQlHandler(WebInterceptor... interceptors) {
		return WebGraphQlHandler.builder(new ExecutionGraphQlService(graphQlSource()))
				.interceptors(Arrays.asList(interceptors))
				.build();
	}

	private static GraphQlSource graphQlSource() {
		RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
		builder.type(TypeRuntimeWiring.newTypeWiring("Query")
				.dataFetcher("bookById", (env) -> {
					Long id = Long.parseLong(env.getArgument("id"));
					return booksMap.get(id);
				}));
		builder.type(TypeRuntimeWiring.newTypeWiring("Subscription")
				.dataFetcher("bookSearch", (env) -> {
					String author = env.getArgument("author");
					return Flux.fromIterable(booksMap.values()).filter((book) -> book.getAuthor().contains(author));
				}));
		return GraphQlSource.builder()
				.schemaResources(new ClassPathResource("books/schema.graphqls"))
				.runtimeWiring(builder.build())
				.build();
	}

}
