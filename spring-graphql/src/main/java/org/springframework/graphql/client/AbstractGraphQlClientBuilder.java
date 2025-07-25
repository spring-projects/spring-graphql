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

package org.springframework.graphql.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.client.GraphQlClientInterceptor.Chain;
import org.springframework.graphql.client.GraphQlClientInterceptor.SubscriptionChain;
import org.springframework.graphql.client.json.GraphQlJackson2Module;
import org.springframework.graphql.client.json.GraphQlJacksonModule;
import org.springframework.graphql.support.CachingDocumentSource;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;


/**
 * Abstract, base class for transport specific {@link GraphQlClient.Builder}
 * implementations.
 *
 * <p>Subclasses must implement {@link #build()} and call
 * {@link #buildGraphQlClient(GraphQlTransport)} to obtain a default, transport
 * agnostic {@code GraphQlClient}. A transport-specific extension can then wrap
 * this default tester by extending {@link AbstractDelegatingGraphQlClient}.
 *
 * @param <B> the type of builder
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see AbstractDelegatingGraphQlClient
 */
public abstract class AbstractGraphQlClientBuilder<B extends AbstractGraphQlClientBuilder<B>> implements GraphQlClient.Builder<B> {

	protected static final boolean jacksonPresent = ClassUtils.isPresent(
			"tools.jackson.databind.ObjectMapper", AbstractGraphQlClientBuilder.class.getClassLoader());

	protected static final boolean jackson2Present = ClassUtils.isPresent(
			"com.fasterxml.jackson.databind.ObjectMapper", AbstractGraphQlClientBuilder.class.getClassLoader());

	private final List<GraphQlClientInterceptor> interceptors = new ArrayList<>();

	private DocumentSource documentSource;

	private @Nullable Encoder<?> jsonEncoder;

	private @Nullable Decoder<?> jsonDecoder;

	private @Nullable Duration blockingTimeout;


	/**
	 * Default constructor for use from subclasses.
	 * <p>Subclasses must set the transport to use before {@link #build()} or
	 * during, by overriding {@link #build()}.
	 */
	protected AbstractGraphQlClientBuilder() {
		this.documentSource = initDocumentSource();
	}

	private static DocumentSource initDocumentSource() {
		return new CachingDocumentSource(new ResourceDocumentSource(
				Collections.singletonList(new ClassPathResource("graphql-documents/")),
				ResourceDocumentSource.FILE_EXTENSIONS));
	}

	@Override
	public B interceptor(GraphQlClientInterceptor... interceptors) {
		this.interceptors.addAll(Arrays.asList(interceptors));
		return self();
	}

	@Override
	public B interceptors(Consumer<List<GraphQlClientInterceptor>> interceptorsConsumer) {
		interceptorsConsumer.accept(this.interceptors);
		return self();
	}

	@Override
	public B documentSource(DocumentSource documentSource) {
		this.documentSource = documentSource;
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
	 * @param encoder the JSON encoder
	 * @param decoder the JSON decoder
	 */
	protected void setJsonCodecs(Encoder<?> encoder, Decoder<?> decoder) {
		this.jsonEncoder = encoder;
		this.jsonDecoder = decoder;
	}

	/**
	 * Variant of {@link #setJsonCodecs} for setting each codec individually.
	 * @param encoder the JSON encoder
	 */
	protected void setJsonEncoder(Encoder<?> encoder) {
		this.jsonEncoder = encoder;
	}

	/**
	 * Access to the configured JSON encoder.
	 */
	protected Encoder<?> getJsonEncoder() {
		Assert.notNull(this.jsonEncoder, "JSON Encoder not set");
		return this.jsonEncoder;
	}

	/**
	 * Variant of {@link #setJsonCodecs} for setting each codec individually.
	 * @param decoder the JSON decoder
	 */
	protected void setJsonDecoder(Decoder<?> decoder) {
		this.jsonDecoder = decoder;
	}

	/**
	 * Access to the configured JSON encoder.
	 */
	protected Decoder<?> getJsonDecoder() {
		Assert.notNull(this.jsonDecoder, "JSON Encoder not set");
		return this.jsonDecoder;
	}

	/**
	 * Return the configured interceptors. For subclasses that look for a
	 * transport specific interceptor extensions.
	 */
	protected List<GraphQlClientInterceptor> getInterceptors() {
		return this.interceptors;
	}

	/**
	 * Build the default transport-agnostic client that subclasses can then wrap
	 * with {@link AbstractDelegatingGraphQlClient}.
	 * @param transport the GraphQL transport to be used by the client
	 */
	protected GraphQlClient buildGraphQlClient(GraphQlTransport transport) {

		if (jacksonPresent) {
			this.jsonEncoder = (this.jsonEncoder == null) ? DefaultJacksonCodecs.encoder() : this.jsonEncoder;
			this.jsonDecoder = (this.jsonDecoder == null) ? DefaultJacksonCodecs.decoder() : this.jsonDecoder;
		}
		else if (jackson2Present) {
			this.jsonEncoder = (this.jsonEncoder == null) ? DefaultJackson2Codecs.encoder() : this.jsonEncoder;
			this.jsonDecoder = (this.jsonDecoder == null) ? DefaultJackson2Codecs.decoder() : this.jsonDecoder;
		}

		return new DefaultGraphQlClient(this.documentSource,
				createExecuteChain(transport), createSubscriptionChain(transport), this.blockingTimeout);
	}

	/**
	 * Return a {@code Consumer} to initialize new builders from "this" builder.
	 */
	protected Consumer<AbstractGraphQlClientBuilder<?>> getBuilderInitializer() {
		return (builder) -> {
			builder.interceptors((interceptorList) -> interceptorList.addAll(this.interceptors));
			builder.documentSource(this.documentSource);
			builder.setJsonCodecs(getEncoder(), getDecoder());
		};
	}

	private Chain createExecuteChain(GraphQlTransport transport) {

		Chain chain = (request) -> transport.execute(request)
				.map((response) -> new DefaultClientGraphQlResponse(request, response, getEncoder(), getDecoder()));

		return this.interceptors.stream()
				.reduce(GraphQlClientInterceptor::andThen)
				.map((i) -> (Chain) (request) -> i.intercept(request, chain))
				.orElse(chain);
	}

	private SubscriptionChain createSubscriptionChain(GraphQlTransport transport) {

		SubscriptionChain chain = (request) -> transport
				.executeSubscription(request)
				.map((response) -> new DefaultClientGraphQlResponse(request, response, getEncoder(), getDecoder()));

		return this.interceptors.stream()
				.reduce(GraphQlClientInterceptor::andThen)
				.map((i) -> (SubscriptionChain) (request) -> i.interceptSubscription(request, chain))
				.orElse(chain);
	}

	private Encoder<?> getEncoder() {
		Assert.notNull(this.jsonEncoder, "jsonEncoder has not been set");
		return this.jsonEncoder;
	}

	private Decoder<?> getDecoder() {
		Assert.notNull(this.jsonDecoder, "jsonDecoder has not been set");
		return this.jsonDecoder;
	}


	protected static class DefaultJacksonCodecs {

		private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
				.addModule(new GraphQlJacksonModule()).build();

		static Encoder<?> encoder() {
			return new JacksonJsonEncoder(JSON_MAPPER, MediaType.APPLICATION_JSON);
		}

		static Decoder<?> decoder() {
			return new JacksonJsonDecoder(JSON_MAPPER, MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
		}

	}

	@SuppressWarnings("removal")
	protected static class DefaultJackson2Codecs {

		private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
				Jackson2ObjectMapperBuilder.json().modulesToInstall(new GraphQlJackson2Module()).build();

		static Encoder<?> encoder() {
			return new Jackson2JsonEncoder(JSON_MAPPER, MediaType.APPLICATION_JSON);
		}

		static Decoder<?> decoder() {
			return new Jackson2JsonDecoder(JSON_MAPPER, MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
		}
	}

}
