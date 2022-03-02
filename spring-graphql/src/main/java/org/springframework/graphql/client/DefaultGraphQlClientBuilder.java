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
 * Default {@link GraphQlClient.Builder} implementation that builds a
 * {@link GraphQlClient} for use with any transport.
 *
 * <p>Intended for use as a base class for builders that do assist with building
 * the underlying transport. Such extension
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultGraphQlClientBuilder<B extends DefaultGraphQlClientBuilder<B>> implements GraphQlClient.Builder<B> {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = DefaultGraphQlClientBuilder.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}


	@Nullable
	private GraphQlTransport transport;

	@Nullable
	private DocumentSource documentSource;

	/**
	 * Constructor with a given transport instance.
	 */
	DefaultGraphQlClientBuilder(GraphQlTransport transport) {
		Assert.notNull(transport, "GraphQlTransport is required");
		this.transport = transport;
	}

	/**
	 * Constructor for subclass builders that will call
	 * {@link #transport(GraphQlTransport)} to set the transport instance
	 * before {@link #build()}.
	 */
	DefaultGraphQlClientBuilder() {
	}

	protected void transport(GraphQlTransport transport) {
		this.transport = transport;
	}

	@Override
	public B documentSource(@Nullable DocumentSource contentLoader) {
		this.documentSource = contentLoader;
		return self();
	}

	@SuppressWarnings("unchecked")
	private <T extends B> T self() {
		return (T) this;
	}

	@Override
	public GraphQlClient build() {
		Assert.notNull(this.transport, "No GraphQlTransport. Has a subclass not initialized it?");
		return new DefaultGraphQlClient(this.transport, initJsonPathConfig(), initDocumentSource(), getBuilderInitializer());
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
	 * Exposes a {@code Consumer} to subclasses to initialize new builder instances
	 * from the configuration of "this" builder.
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
