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

package org.springframework.graphql.client;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookCriteria;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.webflux.GraphQlWebSocketHandler;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test making requests through {@link WebSocketGraphQlClient} to
 * {@link GraphQlWebSocketHandler} via a mock WebSocket connection.
 *
 * @author Rossen Stoyanchev
 */
public class WebSocketGraphQlClientTests {

	private final TestWebSocketClient webSocketClient = initWebSocketClient();


	@Test
	void query() {
		String document = "{ " +
				"  booksByCriteria(criteria: {author:\"Orwell\"}) { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		List<Book> books = WebSocketGraphQlClient.create(URI.create("/"), this.webSocketClient)
				.document(document)
				.execute()
				.map(response -> response.toEntityList("booksByCriteria", Book.class))
				.block(Duration.ofSeconds(5));

		assertThat(books).hasSize(2);
		assertThat(books.get(0).getName()).isEqualTo("Nineteen Eighty-Four");
		assertThat(books.get(1).getName()).isEqualTo("Animal Farm");
	}

	@Test
	void subscription() {
		String document = "subscription { " +
				"  bookSearch(author:\"Orwell\") { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Flux<Book> bookFlux = WebSocketGraphQlClient.create(URI.create("/"), this.webSocketClient)
				.document(document)
				.executeSubscription()
				.map(response -> response.toEntity("bookSearch", Book.class));

		StepVerifier.create(bookFlux)
				.consumeNextWith(book -> {
					assertThat(book.getId()).isEqualTo(1);
					assertThat(book.getName()).isEqualTo("Nineteen Eighty-Four");
				})
				.consumeNextWith(book -> {
					assertThat(book.getId()).isEqualTo(5);
					assertThat(book.getName()).isEqualTo("Animal Farm");
				})
				.verifyComplete();
	}


	private TestWebSocketClient initWebSocketClient() {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BookController.class);
		context.refresh();

		WebGraphQlHandler webGraphQlHandler = GraphQlSetup.schemaResource(BookSource.schema)
				.runtimeWiringForAnnotatedControllers(context)
				.toWebGraphQlHandler();

		WebSocketHandler webSocketServerHandler = new GraphQlWebSocketHandler(
				webGraphQlHandler, ClientCodecConfigurer.create(), Duration.ofSeconds(5));

		return new TestWebSocketClient(webSocketServerHandler);
	}



	@SuppressWarnings("unused")
	@Controller
	private static class BookController {

		@QueryMapping
		public List<Book> booksByCriteria(@Argument BookCriteria criteria) {
			return BookSource.findBooksByAuthor(criteria.getAuthor());
		}

		@SubscriptionMapping
		public Flux<Book> bookSearch(@Argument String author) {
			return Flux.fromIterable(BookSource.findBooksByAuthor(author)).delayElements(Duration.ofMillis(50));
		}

	}

}
