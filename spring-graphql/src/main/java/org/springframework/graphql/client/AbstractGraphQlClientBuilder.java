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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import org.springframework.graphql.support.CachingDocumentSource;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
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

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = AbstractGraphQlClientBuilder.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}


	@Nullable
	private DocumentSource documentSource;


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

	/**
	 * Subclasses call this from {@link #build()} to provide the transport and get
	 * the default {@code GraphQlClient to delegate to for request execution.
	 */
	protected GraphQlClient buildGraphQlClient(GraphQlTransport transport) {
		Assert.notNull(transport, "GraphQlTransport is required");
		return new DefaultGraphQlClient(transport, initJsonPathConfig(), initDocumentSource(), getBuilderInitializer());
	}

	private Configuration initJsonPathConfig() {
		// Allow configuring JSONPath with codecs from transport subclasses
		return (jackson2Present ? Jackson2Configuration.create() : Configuration.builder().build());
	}

	private DocumentSource initDocumentSource() {
		return (this.documentSource == null ?
				new CachingDocumentSource(new ResourceDocumentSource()) : this.documentSource);
	}

	/**
	 * Subclasses call this from {@link #build()} to obtain a {@code Consumer} to
	 * initialize new builder instances with, based on "this" builder.
	 */
	protected Consumer<GraphQlClient.Builder<?>> getBuilderInitializer() {
		return builder -> {
			if (this.documentSource != null) {
				builder.documentSource(documentSource);
			}
		};
	}


	private static class Jackson2Configuration {

		static Configuration create() {
			return Configuration.builder()
					.jsonProvider(new JacksonJsonProvider())
					.mappingProvider(new JacksonMappingProvider())
					.build();
		}
	}

}
