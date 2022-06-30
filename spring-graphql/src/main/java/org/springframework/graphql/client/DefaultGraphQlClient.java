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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
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

	private final GraphQlClientInterceptor.Chain executeChain;

    private final GraphQlClientInterceptor.Chain fileUploadChain;

    private final GraphQlClientInterceptor.SubscriptionChain executeSubscriptionChain;


	DefaultGraphQlClient(
			DocumentSource documentSource, GraphQlClientInterceptor.Chain executeChain,
            GraphQlClientInterceptor.Chain fileUploadChain,
			GraphQlClientInterceptor.SubscriptionChain executeSubscriptionChain) {

		Assert.notNull(documentSource, "DocumentSource is required");
		Assert.notNull(executeChain, "GraphQlClientInterceptor.Chain is required");
        Assert.notNull(fileUploadChain, "GraphQlClientInterceptor.Chain is required");
        Assert.notNull(executeSubscriptionChain, "GraphQlClientInterceptor.SubscriptionChain is required");

		this.documentSource = documentSource;
		this.executeChain = executeChain;
        this.fileUploadChain = fileUploadChain;
        this.executeSubscriptionChain = executeSubscriptionChain;
	}


	@Override
	public RequestSpec document(String document) {
		return new DefaultRequestSpec(Mono.just(document));
	}

	@Override
	public RequestSpec documentName(String name) {
		return new DefaultRequestSpec(this.documentSource.getDocument(name));
	}

	/**
	 * The default client is unaware of transport details, and cannot implement
	 * mutate directly. It should be wrapped from transport aware extensions via
	 * {@link AbstractDelegatingGraphQlClient} that also implement mutate.
	 */
	@Override
	public Builder<?> mutate() {
		throw new UnsupportedOperationException();
	}


	/**
	 * Default {@link RequestSpec} implementation.
	 */
	private final class DefaultRequestSpec implements RequestSpec {

		private final Mono<String> documentMono;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private final Map<String, Object> attributes = new LinkedHashMap<>();

		private final Map<String, Object> extensions = new LinkedHashMap<>();

        private final Map<String, Object> fileVariables = new LinkedHashMap<>();

        DefaultRequestSpec(Mono<String> documentMono) {
			Assert.notNull(documentMono, "'document' is required");
			this.documentMono = documentMono;
		}

		@Override
		public DefaultRequestSpec operationName(@Nullable String operationName) {
			this.operationName = operationName;
			return this;
		}

		@Override
		public DefaultRequestSpec variable(String name, @Nullable Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public RequestSpec variables(Map<String, Object> variables) {
			this.variables.putAll(variables);
			return this;
		}

        @Override
        public DefaultRequestSpec fileVariable(String name, Object value) {
            Assert.notNull(name, "'name' is required");
            Assert.notNull(value, "'value' is required");
            this.fileVariables.put(name, value);
            return this;
        }

        @Override
        public RequestSpec fileVariables(Map<String, Object> files) {
            this.fileVariables.putAll(files);
            return this;
        }

		@Override
		public RequestSpec extension(String name, Object value) {
			this.extensions.put(name, value);
			return this;
		}

		@Override
		public RequestSpec extensions(Map<String, Object> extensions) {
			this.extensions.putAll(extensions);
			return this;
		}

		@Override
		public RequestSpec attribute(String name, Object value) {
			this.attributes.put(name, value);
			return this;
		}

		@Override
		public RequestSpec attributes(Consumer<Map<String, Object>> attributesConsumer) {
			attributesConsumer.accept(this.attributes);
			return this;
		}

		@Override
		public RetrieveSpec retrieve(String path) {
			return new DefaultRetrieveSpec(execute(), path);
		}

		@Override
		public RetrieveSubscriptionSpec retrieveSubscription(String path) {
			return new DefaultRetrieveSubscriptionSpec(executeSubscription(), path);
		}

		@Override
		public Mono<ClientGraphQlResponse> execute() {
			return initRequest().flatMap(request -> executeChain.next(request)
					.onErrorResume(
							ex -> !(ex instanceof GraphQlClientException),
							ex -> Mono.error(new GraphQlTransportException(ex, request))));
		}

        @Override
        public Mono<ClientGraphQlResponse> executeFileUpload() {
            return initFileUploadRequest().flatMap(request -> fileUploadChain.next(request)
                    .onErrorResume(
                            ex -> !(ex instanceof GraphQlClientException),
                            ex -> Mono.error(new GraphQlTransportException(ex, request))));
        }

		@Override
		public Flux<ClientGraphQlResponse> executeSubscription() {
			return initRequest().flatMapMany(request -> executeSubscriptionChain.next(request)
					.onErrorResume(
							ex -> !(ex instanceof GraphQlClientException),
							ex -> Mono.error(new GraphQlTransportException(ex, request))));
		}

		private Mono<ClientGraphQlRequest> initRequest() {
			return this.documentMono.map(document ->
					new DefaultClientGraphQlRequest(document, this.operationName, this.variables, this.extensions, this.attributes));
		}

        private Mono<ClientGraphQlRequest> initFileUploadRequest() {
            return this.documentMono.map(document ->
                    new MultipartClientGraphQlRequest(document, this.operationName, this.variables, this.extensions, this.attributes, this.fileVariables));
        }

	}


	private static class RetrieveSpecSupport {

		private final String path;

		protected RetrieveSpecSupport(String path) {
			this.path = path;
		}

		/**
		 * Return the field if valid, or {@code null} if {@code null} without errors.
		 * @throws FieldAccessException for invalid response or failed field
		 */
		@Nullable
		protected ClientResponseField getValidField(ClientGraphQlResponse response) {
			ClientResponseField field = response.field(this.path);
			if (!response.isValid() || field.getError() != null) {
				throw new FieldAccessException(
						((DefaultClientGraphQlResponse) response).getRequest(), response, field);
			}
			return (field.hasValue() ? field : null);
		}

	}


	private static class DefaultRetrieveSpec extends RetrieveSpecSupport implements RetrieveSpec {

		private final Mono<ClientGraphQlResponse> responseMono;

		DefaultRetrieveSpec(Mono<ClientGraphQlResponse> responseMono, String path) {
			super(path);
			this.responseMono = responseMono;
		}

		@Override
		public <D> Mono<D> toEntity(Class<D> entityType) {
			return this.responseMono.mapNotNull(this::getValidField).map(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Mono<D> toEntity(ParameterizedTypeReference<D> entityType) {
			return this.responseMono.mapNotNull(this::getValidField).map(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Mono<List<D>> toEntityList(Class<D> elementType) {
			return this.responseMono.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

		@Override
		public <D> Mono<List<D>> toEntityList(ParameterizedTypeReference<D> elementType) {
			return this.responseMono.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

	}


	private static class DefaultRetrieveSubscriptionSpec extends RetrieveSpecSupport implements RetrieveSubscriptionSpec {

		private final Flux<ClientGraphQlResponse> responseFlux;

		DefaultRetrieveSubscriptionSpec(Flux<ClientGraphQlResponse> responseFlux, String path) {
			super(path);
			this.responseFlux = responseFlux;
		}

		@Override
		public <D> Flux<D> toEntity(Class<D> entityType) {
			return this.responseFlux.mapNotNull(this::getValidField).map(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Flux<D> toEntity(ParameterizedTypeReference<D> entityType) {
			return this.responseFlux.mapNotNull(this::getValidField).map(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Flux<List<D>> toEntityList(Class<D> elementType) {
			return this.responseFlux.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

		@Override
		public <D> Flux<List<D>> toEntityList(ParameterizedTypeReference<D> elementType) {
			return this.responseFlux.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

	}

}
