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
package org.springframework.graphql.test.tester;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import graphql.GraphQLError;

import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.support.CachingDocumentSource;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Abstract, base class for transport specific {@link GraphQlTester.Builder}
 * implementations.
 *
 * <p>Subclasses must implement {@link #build()} and call
 * {@link #buildGraphQlTester(GraphQlTransport)} to obtain a default, transport
 * agnostic {@code GraphQlTester}. A transport specific extension can then wrap
 * this default tester by extending {@link AbstractDelegatingGraphQlTester}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see AbstractDelegatingGraphQlTester
 */
public abstract class AbstractGraphQlTesterBuilder<B extends AbstractGraphQlTesterBuilder<B>> implements GraphQlTester.Builder<B> {

	private static final boolean jackson2Present;

	static {
		ClassLoader classLoader = AbstractGraphQlTesterBuilder.class.getClassLoader();
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
				&& ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
	}

	private static final Duration DEFAULT_RESPONSE_DURATION = Duration.ofSeconds(5);


	@Nullable
	private Predicate<GraphQLError> errorFilter;

	private DocumentSource documentSource = new CachingDocumentSource(new ResourceDocumentSource());

	private Duration responseTimeout = DEFAULT_RESPONSE_DURATION;


	@Override
	public B errorFilter(Predicate<GraphQLError> predicate) {
		this.errorFilter = (this.errorFilter != null ? errorFilter.and(predicate) : predicate);
		return self();
	}

	@Override
	public B documentSource(DocumentSource documentSource) {
		this.documentSource = documentSource;
		return self();
	}

	@Override
	public B responseTimeout(Duration timeout) {
		Assert.notNull(timeout, "'timeout' is required");
		this.responseTimeout = timeout;
		return self();
	}

	@SuppressWarnings("unchecked")
	private <T extends B> T self() {
		return (T) this;
	}

	/**
	 * Subclasses call this from {@link #build()} to provide the transport and get
	 * the default {@code GraphQlTester} to delegate to for request execution.
	 */
	protected GraphQlTester buildGraphQlTester(GraphQlTransport transport) {
		Assert.notNull(transport, "GraphQlTransport is required");
		return new DefaultGraphQlTester(
				transport, this.errorFilter, initJsonPathConfig(), this.documentSource, this.responseTimeout,
				getBuilderInitializer());
	}

	private Configuration initJsonPathConfig() {
		// Allow configuring JSONPath with codecs from transport subclasses
		return (jackson2Present ? Jackson2Configuration.create() : Configuration.builder().build());
	}

	/**
	 * Subclasses call this from {@link #build()} to obtain a {@code Consumer} to
	 * initialize new builder instances with, based on "this" builder.
	 */
	protected Consumer<GraphQlTester.Builder<?>> getBuilderInitializer() {
		return builder -> {
			if (this.errorFilter != null) {
				builder.errorFilter(this.errorFilter);
			}
			builder.documentSource(this.documentSource);
			builder.responseTimeout(this.responseTimeout);
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
