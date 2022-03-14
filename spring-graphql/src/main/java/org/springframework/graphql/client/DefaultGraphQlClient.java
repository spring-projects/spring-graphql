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

import com.jayway.jsonpath.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
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

	private final GraphQlTransport transport;

	private final Configuration jsonPathConfig;

	private final DocumentSource documentSource;

	private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;


	DefaultGraphQlClient(
			GraphQlTransport transport, Configuration jsonPathConfig, DocumentSource documentSource,
			Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

		Assert.notNull(transport, "GraphQlTransport is required");
		Assert.notNull(jsonPathConfig, "JSONPath Configuration is required");
		Assert.notNull(documentSource, "DocumentSource is required");
		Assert.notNull(builderInitializer, "`builderInitializer` is required");

		this.transport = transport;
		this.jsonPathConfig = jsonPathConfig;
		this.documentSource = documentSource;
		this.builderInitializer = builderInitializer;
	}


	@Override
	public Request document(String document) {
		return new DefaultRequest(Mono.just(document), this.transport, this.jsonPathConfig);
	}

	@Override
	public Request documentName(String name) {
		Mono<String> document = this.documentSource.getDocument(name);
		return new DefaultRequest(document, this.transport, this.jsonPathConfig);
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
	private static final class DefaultRequest implements Request {

		private final Mono<String> documentMono;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private final GraphQlTransport transport;

		private final Configuration jsonPathConfig;

		DefaultRequest(Mono<String> documentMono, GraphQlTransport transport, Configuration jsonPathConfig) {
			Assert.notNull(documentMono, "'document' is required");
			this.documentMono = documentMono;
			this.transport = transport;
			this.jsonPathConfig = jsonPathConfig;
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
					this.transport.execute(request)
							.map(result ->
									new DefaultClientGraphQlResponse(request, result, this.jsonPathConfig))
							.onErrorResume(
									ex -> !(ex instanceof GraphQlClientException),
									ex -> toGraphQlTransportException(ex, request)));
		}

		@Override
		public Flux<ClientGraphQlResponse> executeSubscription() {
			return initRequest().flatMapMany(request ->
					this.transport.executeSubscription(request)
							.map(result ->
									new DefaultClientGraphQlResponse(request, result, this.jsonPathConfig))
							.onErrorResume(
									ex -> !(ex instanceof GraphQlClientException),
									ex -> toGraphQlTransportException(ex, request)));
		}

		private Mono<GraphQlRequest> initRequest() {
			return this.documentMono.map(document ->
					new GraphQlRequest(document, this.operationName, this.variables));
		}

		private <T> Mono<T> toGraphQlTransportException(Throwable ex, GraphQlRequest request) {
			return Mono.error(new GraphQlTransportException(ex, request));
		}

	}


}
