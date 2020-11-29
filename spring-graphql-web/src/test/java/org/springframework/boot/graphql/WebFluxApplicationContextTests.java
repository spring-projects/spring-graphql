package org.springframework.boot.graphql;


import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.GraphQLDataFetchers;
import org.springframework.graphql.WebFluxGraphQLHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.adapter.AbstractWebSocketSession;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.assertThat;

class WebFluxApplicationContextTests {

	private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
			HttpHandlerAutoConfiguration.class, WebFluxAutoConfiguration.class,
			CodecsAutoConfiguration.class, JacksonAutoConfiguration.class,
			GraphQLAutoConfiguration.class, WebFluxGraphQLAutoConfiguration.class);

	private static final String BASE_URL = "https://spring.example.org/graphql";


	@Test
	void query() {
		testWithWebClient(client -> {
			String query = "{" +
					"  bookById(id: \\\"book-1\\\"){ " +
					"    id" +
					"    name" +
					"    pageCount" +
					"    author" +
					"  }" +
					"}";

			client.post().uri("")
					.bodyValue("{  \"query\": \"" + query + "\"}")
					.exchange()
					.expectStatus().isOk()
					.expectBody().jsonPath("data.bookById.name").isEqualTo("GraphQL for beginners");
		});
	}

	@Test
	void queryMissing() {
		testWithWebClient(client -> client.post().uri("").bodyValue("{}").exchange().expectStatus().isBadRequest());
	}

	@Test
	void queryIsInvalidJson() {
		testWithWebClient(client -> client.post().uri("").bodyValue(":)").exchange().expectStatus().isBadRequest());
	}

	@Test
	void subscription() {
		testWithApplicationContext(context -> {
			String query =
					"{ \"query\": \"" +
							"  subscription TestSubscription {" +
							"    bookSearch(minPages: 200) {" +
							"      id" +
							"      name" +
							"      pageCount" +
							"      author" +
							"  }" +
							"}" +
							"\"}";

			DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(query.getBytes(StandardCharsets.UTF_8));
			Flux<WebSocketMessage> input = Flux.just(new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer));
			TestWebSocketSession session = new TestWebSocketSession("1", URI.create(BASE_URL), input);

			context.getBean(WebFluxGraphQLHandler.class)
					.getSubscriptionWebSocketHandler().handle(session).block();

			StepVerifier.create(session.getOutput())
					.consumeNextWith(message -> assertThat(extractBook(message)).containsEntry("id", "book-2"))
					.consumeNextWith(message -> assertThat(extractBook(message)).containsEntry("id", "book-3"))
					.consumeNextWith(message -> assertThat(extractBook(message)).containsEntry("id", "book-3"))
					.verifyComplete();
		});
	}

	@SuppressWarnings({"unchecked", "ConstantConditions"})
	private Map<String, Object> extractBook(WebSocketMessage message) {
		Map<String, Object> map = (Map<String, Object>) new Jackson2JsonDecoder().decode(
				DataBufferUtils.retain(message.getPayload()),
				ResolvableType.forClass(Map.class), null, Collections.emptyMap());
		return (Map<String, Object>) map.get("bookSearch");
	}

	private void testWithWebClient(Consumer<WebTestClient> consumer) {
		testWithApplicationContext(context -> {
			WebTestClient client = WebTestClient.bindToApplicationContext(context)
					.configureClient()
					.defaultHeaders(headers -> {
						headers.setContentType(MediaType.APPLICATION_JSON);
						headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
					})
					.baseUrl(BASE_URL)
					.build();
			consumer.accept(client);
		});
	}

	private void testWithApplicationContext(ContextConsumer<ApplicationContext> consumer) {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AUTO_CONFIGURATIONS)
				.withUserConfiguration(DataFetchersConfiguration.class)
				.withPropertyValues(
						"spring.main.web-application-type=reactive",
						"spring.graphql.schema-location:classpath:books/schema.graphqls")
				.run(consumer);
	}


	@Configuration(proxyBeanMethods = false)
	static class DataFetchersConfiguration {

		@Bean
		public RuntimeWiringCustomizer bookDataFetcher() {
			return (runtimeWiring) -> {
				runtimeWiring.type(newTypeWiring("Query")
						.dataFetcher("bookById", GraphQLDataFetchers.getBookByIdDataFetcher()));
				runtimeWiring.type(newTypeWiring("Subscription")
						.dataFetcher("bookSearch", GraphQLDataFetchers.getBooksOnSale()));
			};
		}
	}


	private static class TestWebSocketSession extends AbstractWebSocketSession<Object> {

		private final Flux<WebSocketMessage> input;

		private Flux<WebSocketMessage> output;

		public TestWebSocketSession(String id, URI uri, Flux<WebSocketMessage> input) {
			super(new Object(), id,
					new HandshakeInfo(uri, new HttpHeaders(), Mono.empty(), null),
					DefaultDataBufferFactory.sharedInstance);
			this.input = input;
		}

		@Override
		public Flux<WebSocketMessage> receive() {
			return this.input;
		}

		@Override
		public Mono<Void> send(Publisher<WebSocketMessage> messages) {
			this.output = Flux.from(messages);
			return Mono.empty();
		}

		public Flux<WebSocketMessage> getOutput() {
			return this.output;
		}

		@Override
		public boolean isOpen() {
			throw new java.lang.UnsupportedOperationException();
		}

		@Override
		public Mono<Void> close(CloseStatus status) {
			throw new java.lang.UnsupportedOperationException();
		}

		@Override
		public Mono<CloseStatus> closeStatus() {
			throw new java.lang.UnsupportedOperationException();
		}
	}

}
