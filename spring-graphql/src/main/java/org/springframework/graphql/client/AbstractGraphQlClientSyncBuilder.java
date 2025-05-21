/*
 * Copyright 2002-2024 the original author or authors.
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.client.SyncGraphQlClientInterceptor.Chain;
import org.springframework.graphql.support.CachingDocumentSource;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;


/**
 * Abstract, base class for transport specific {@link GraphQlClient.SyncBuilder}
 * implementations.
 *
 * <p>Subclasses must implement {@link #build()} and call
 * {@link #buildGraphQlClient(SyncGraphQlTransport)} to obtain a default, transport
 * agnostic {@code GraphQlClient}. A transport specific extension can then wrap
 * this default tester by extending {@link AbstractDelegatingGraphQlClient}.
 *
 * @param <B> the type of builder
 * @author Rossen Stoyanchev
 * @since 1.3.0
 * @see AbstractDelegatingGraphQlClient
 */
public abstract class AbstractGraphQlClientSyncBuilder<B extends AbstractGraphQlClientSyncBuilder<B>>
		implements GraphQlClient.SyncBuilder<B> {

	protected static final boolean jackson2Present = ClassUtils.isPresent(
			"com.fasterxml.jackson.databind.ObjectMapper", AbstractGraphQlClientSyncBuilder.class.getClassLoader());


	private final List<SyncGraphQlClientInterceptor> interceptors = new ArrayList<>();

	private DocumentSource documentSource;

	private @Nullable HttpMessageConverter<Object> jsonConverter;

	private Scheduler scheduler = Schedulers.boundedElastic();

	private @Nullable Duration blockingTimeout;

	/**
	 * Default constructor for use from subclasses.
	 * <p>Subclasses must set the transport to use before {@link #build()} or
	 * during, by overriding {@link #build()}.
	 */
	protected AbstractGraphQlClientSyncBuilder() {
		this.documentSource = initDocumentSource();
	}

	private static DocumentSource initDocumentSource() {
		return new CachingDocumentSource(new ResourceDocumentSource(
				Collections.singletonList(new ClassPathResource("graphql-documents/")),
				ResourceDocumentSource.FILE_EXTENSIONS));
	}

	@Override
	public B interceptor(SyncGraphQlClientInterceptor... interceptors) {
		Collections.addAll(this.interceptors, interceptors);
		return self();
	}

	@Override
	public B interceptors(Consumer<List<SyncGraphQlClientInterceptor>> interceptorsConsumer) {
		interceptorsConsumer.accept(this.interceptors);
		return self();
	}

	@Override
	public B documentSource(DocumentSource documentSource) {
		this.documentSource = documentSource;
		return self();
	}

	@Override
	public B scheduler(Scheduler scheduler) {
		Assert.notNull(scheduler, "Scheduler is required");
		this.scheduler = scheduler;
		return self();
	}

	@Override
	public B blockingTimeout(@Nullable Duration blockingTimeout) {
		this.blockingTimeout = blockingTimeout;
		return self();
	}

	@SuppressWarnings("unchecked")
	private <T extends B> T self() {
		return (T) this;
	}


	// Protected methods for use from build() in subclasses


	/**
	 * Transport-specific subclasses can provide their JSON {@code Encoder} and
	 * {@code Decoder} for use at the client level, for mapping response data
	 * to some target entity type.
	 * @param converter the message converter for JSON payloads
	 */
	protected void setJsonConverter(HttpMessageConverter<Object> converter) {
		this.jsonConverter = converter;
	}


	/**
	 * Build the default transport-agnostic client that subclasses can then wrap
	 * with {@link AbstractDelegatingGraphQlClient}.
	 * @param transport the GraphQL transport to be used by the client
	 */
	protected GraphQlClient buildGraphQlClient(SyncGraphQlTransport transport) {

		if (jackson2Present) {
			this.jsonConverter = (this.jsonConverter == null) ?
					DefaultJacksonConverter.initialize() : this.jsonConverter;
		}

		return new DefaultGraphQlClient(
				this.documentSource, createExecuteChain(transport), this.scheduler, this.blockingTimeout);
	}

	/**
	 * Return a {@code Consumer} to initialize new builders from "this" builder.
	 */
	protected Consumer<AbstractGraphQlClientSyncBuilder<?>> getBuilderInitializer() {
		return (builder) -> {
			builder.interceptors((interceptorList) -> interceptorList.addAll(this.interceptors));
			builder.documentSource(this.documentSource);
			builder.setJsonConverter(getJsonConverter());
		};
	}

	private Chain createExecuteChain(SyncGraphQlTransport transport) {

		Encoder<?> encoder = HttpMessageConverterDelegate.asEncoder(getJsonConverter());
		Decoder<?> decoder = HttpMessageConverterDelegate.asDecoder(getJsonConverter());

		Chain chain = (request) -> {
			GraphQlResponse response = transport.execute(request);
			return new DefaultClientGraphQlResponse(request, response, encoder, decoder);
		};

		return this.interceptors.stream()
				.reduce(SyncGraphQlClientInterceptor::andThen)
				.map((i) -> (Chain) (request) -> i.intercept(request, chain))
				.orElse(chain);
	}

	private HttpMessageConverter<Object> getJsonConverter() {
		Assert.notNull(this.jsonConverter, "jsonConverter has not been set");
		return this.jsonConverter;
	}


	private static final class DefaultJacksonConverter {

		static HttpMessageConverter<Object> initialize() {
			return new MappingJackson2HttpMessageConverter();
		}
	}

}
