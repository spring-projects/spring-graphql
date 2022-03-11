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
import java.util.function.Function;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import org.springframework.graphql.support.CachingDocumentSource;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
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

	private Configuration jsonPathConfig = Configuration.builder().build();


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
	 * Allow transport-specific subclass builders to register a JSON Path
	 * {@link MappingProvider} that matches the JSON encoding/decoding they use.
	 */
	protected void configureJsonPathConfig(Function<Configuration, Configuration> configurer) {
		this.jsonPathConfig = configurer.apply(this.jsonPathConfig);
	}

	/**
	 * Build the default transport-agnostic client that subclasses can then wrap
	 * with {@link AbstractDelegatingGraphQlClient}.
	 */
	protected GraphQlClient buildGraphQlClient(GraphQlTransport transport) {

		if (jackson2Present) {
			configureJsonPathConfig(Jackson2Configurer::configure);
		}

		return new DefaultGraphQlClient(
				transport, this.jsonPathConfig, this.documentSource, getBuilderInitializer());
	}

	/**
	 * Return a {@code Consumer} to initialize new builders from "this" builder.
	 */
	protected Consumer<AbstractGraphQlClientBuilder<?>> getBuilderInitializer() {
		return builder -> {
			builder.documentSource(documentSource);
			builder.configureJsonPathConfig(config -> this.jsonPathConfig);
		};
	}


	private static class Jackson2Configurer {

		private static final Class<?> defaultMappingProviderType =
				Configuration.defaultConfiguration().mappingProvider().getClass();

		// We only need a MappingProvider:
		// GraphQlTransport returns GraphQlResponse with already parsed JSON

		static Configuration configure(Configuration config) {
			MappingProvider provider = config.mappingProvider();
			if (provider == null || defaultMappingProviderType.isInstance(provider)) {
				config = config.mappingProvider(new JacksonMappingProvider());
			}
			return config;
		}

	}

}
