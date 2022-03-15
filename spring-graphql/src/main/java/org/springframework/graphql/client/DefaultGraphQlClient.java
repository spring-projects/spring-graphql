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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default, final {@link GraphQlClient} implementation for use with any transport.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultGraphQlClient implements GraphQlClient {

	private final DocumentSource documentSource;

	private final GraphQlTransport transport;

	private final Encoder<?> jsonEncoder;

	private final Decoder<?> jsonDecoder;

	private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;


	DefaultGraphQlClient(
			DocumentSource documentSource, GraphQlTransport transport,
			Encoder<?> jsonEncoder, Decoder<?> jsonDecoder,
			Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

		Assert.notNull(documentSource, "DocumentSource is required");
		Assert.notNull(transport, "GraphQlTransport is required");
		Assert.notNull(jsonEncoder, "'jsonEncoder' is required");
		Assert.notNull(jsonEncoder, "'jsonDecoder' is required");
		Assert.notNull(builderInitializer, "`builderInitializer` is required");

		this.documentSource = documentSource;
		this.transport = transport;
		this.jsonEncoder = jsonEncoder;
		this.jsonDecoder = jsonDecoder;
		this.builderInitializer = builderInitializer;
	}


	@Override
	public Request document(String document) {
		return new DefaultRequest(Mono.just(document));
	}

	@Override
	public Request documentName(String name) {
		return new DefaultRequest(this.documentSource.getDocument(name));
	}

	@Override
	public Builder mutate() {
		Builder builder = new Builder(this.transport);
		this.builderInitializer.accept(builder);
		return builder;
	}


	/**
	 * Default {@link GraphQlClient.Builder} with a given transport.
	 */
	static final class Builder extends AbstractGraphQlClientBuilder<Builder> {

		private final GraphQlTransport transport;

		Builder(GraphQlTransport transport) {
			Assert.notNull(transport, "GraphQlTransport is required");
			this.transport = transport;
		}

		@Override
		public GraphQlClient build() {
			return super.buildGraphQlClient(this.transport);
		}

	}


	/**
	 * Default {@link GraphQlClient.Request} implementation.
	 */
	private final class DefaultRequest implements Request {

		private final Mono<String> documentMono;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		DefaultRequest(Mono<String> documentMono) {
			Assert.notNull(documentMono, "'document' is required");
			this.documentMono = documentMono;
		}

		@Override
		public DefaultRequest operationName(@Nullable String operationName) {
			this.operationName = operationName;
			return this;
		}

		@Override
		public DefaultRequest variable(String name, Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public Request variables(Map<String, Object> variables) {
			this.variables.putAll(variables);
			return this;
		}

		@Override
		public Mono<ClientGraphQlResponse> execute() {
			return initRequest().flatMap(request ->
					transport.execute(request)
							.map(response -> initResponse(request, response))
							.onErrorResume(
									ex -> !(ex instanceof GraphQlClientException),
									ex -> toGraphQlTransportException(ex, request)));
		}

		@Override
		public Flux<ClientGraphQlResponse> executeSubscription() {
			return initRequest().flatMapMany(request ->
					transport.executeSubscription(request)
							.map(response -> initResponse(request, response))
							.onErrorResume(
									ex -> !(ex instanceof GraphQlClientException),
									ex -> toGraphQlTransportException(ex, request)));
		}

		private Mono<GraphQlRequest> initRequest() {
			return this.documentMono.map(document ->
					new GraphQlRequest(document, this.operationName, this.variables));
		}

		private DefaultClientGraphQlResponse initResponse(GraphQlRequest request, GraphQlResponse response) {
			return new DefaultClientGraphQlResponse(request, response, jsonEncoder, jsonDecoder);
		}

		private <T> Mono<T> toGraphQlTransportException(Throwable ex, GraphQlRequest request) {
			return Mono.error(new GraphQlTransportException(ex, request));
		}

	}


}
