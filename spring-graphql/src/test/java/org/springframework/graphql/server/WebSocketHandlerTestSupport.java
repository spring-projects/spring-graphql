/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import reactor.core.publisher.Flux;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.server.webmvc.TestWebSocketSession;
import org.springframework.web.socket.WebSocketMessage;

public abstract class WebSocketHandlerTestSupport {

	protected static final String SUBSCRIPTION_ID = "1";

	protected static final String BOOK_QUERY;

	protected static final String BOOK_QUERY_PAYLOAD;

	protected static AtomicBoolean SUBSCRIPTION_CANCELLED = new AtomicBoolean();

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
							.filter((book) -> book.getAuthor().getFullName().contains(author))
							.doOnCancel(() -> SUBSCRIPTION_CANCELLED.set(true));
				})
				.interceptor(interceptors)
				.toWebGraphQlHandler();
	}

	public static class BrokenPipeSession extends TestWebSocketSession {

		@Override
		public void sendMessage(WebSocketMessage<?> message) throws IOException {
			throw new IOException("broken pipe");
		}
	}

	public static class ClosedSession extends TestWebSocketSession {

		private final AtomicBoolean firstSent = new AtomicBoolean();

		@Override
		public void sendMessage(WebSocketMessage<?> message) throws IOException {
			// Let the initial connection_ack through, then simulate the session being
			// concurrently closed: Tomcat raises IllegalStateException (not IOException)
			// on any subsequent send, e.g. while a subscription response is in flight.
			if (this.firstSent.compareAndSet(false, true)) {
				super.sendMessage(message);
				return;
			}
			throw new IllegalStateException(
					"Message will not be sent because the WebSocket session has been closed");
		}
	}

}
