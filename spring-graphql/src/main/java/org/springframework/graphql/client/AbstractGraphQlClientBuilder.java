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

import java.util.function.Consumer;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.support.CachingDocumentSource;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;


/**
 * Abstract, base class for transport specific {@link GraphQlClient.Builder}
 * implementations.
 *
 * <p>Subclasses must implement {@link #build()} and call
 * {@link #buildGraphQlClient(GraphQlTransport)} to obtain a default, transport
 * agnostic {@code GraphQlClient}. A transport specific extension can then wrap
 * this default tester by extending {@link AbstractDelegatingGraphQlClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see AbstractDelegatingGraphQlClient
 */
public abstract class AbstractGraphQlClientBuilder<B extends AbstractGraphQlClientBuilder<B>> implements GraphQlClient.Builder<B> {

	private static final boolean jackson2Present = ClassUtils.isPresent(
			"com.fasterxml.jackson.databind.ObjectMapper", AbstractGraphQlClientBuilder.class.getClassLoader());


	private DocumentSource documentSource = new CachingDocumentSource(new ResourceDocumentSource());

	@Nullable
	private Encoder<?> jsonEncoder;

	@Nullable
	private Decoder<?> jsonDecoder;


	/**
	 * Default constructor for use from subclasses.
	 * <p>Subclasses must set the transport to use before {@link #build()} or
	 * during, by overriding {@link #build()}.
	 */
	protected AbstractGraphQlClientBuilder() {
	}


	@Override
	public B documentSource(DocumentSource contentLoader) {
		this.documentSource = contentLoader;
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
	 */
	protected void setJsonCodecs(Encoder<?> encoder, Decoder<?> decoder) {
		this.jsonEncoder = encoder;
		this.jsonDecoder = decoder;
	}

	/**
	 * Build the default transport-agnostic client that subclasses can then wrap
	 * with {@link AbstractDelegatingGraphQlClient}.
	 */
	protected GraphQlClient buildGraphQlClient(GraphQlTransport transport) {

		if (jackson2Present) {
			this.jsonEncoder = (this.jsonEncoder == null ? Jackson2Configurer.encoder() : this.jsonEncoder);
			this.jsonDecoder = (this.jsonDecoder == null ? Jackson2Configurer.decoder() : this.jsonDecoder);
		}

		return new DefaultGraphQlClient(
				this.documentSource, transport, getJsonEncoder(), getJsonDecoder(), getBuilderInitializer());
	}

	/**
	 * Return a {@code Consumer} to initialize new builders from "this" builder.
	 */
	protected Consumer<AbstractGraphQlClientBuilder<?>> getBuilderInitializer() {
		return builder -> {
			builder.documentSource(documentSource);
			builder.setJsonCodecs(getJsonEncoder(), getJsonDecoder());
		};
	}

	private Encoder<?> getJsonEncoder() {
		Assert.notNull(this.jsonEncoder, "jsonEncoder has not been set");
		return this.jsonEncoder;
	}

	private Decoder<?> getJsonDecoder() {
		Assert.notNull(this.jsonDecoder, "jsonDecoder has not been set");
		return this.jsonDecoder;
	}


	private static class Jackson2Configurer {

		static Encoder<?> encoder() {
			return new Jackson2JsonEncoder();
		}

		static Decoder<?> decoder() {
			return new Jackson2JsonDecoder();
		}

	}

}
