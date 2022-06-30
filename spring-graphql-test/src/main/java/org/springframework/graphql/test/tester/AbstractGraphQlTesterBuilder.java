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
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.AbstractGraphQlClientBuilder;
import org.springframework.graphql.client.GraphQlClient;
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

	private static final boolean jackson2Present = ClassUtils.isPresent(
			"com.fasterxml.jackson.databind.ObjectMapper", AbstractGraphQlClientBuilder.class.getClassLoader());

	private static final Duration DEFAULT_RESPONSE_DURATION = Duration.ofSeconds(5);


	@Nullable
	private Predicate<ResponseError> errorFilter;

	private DocumentSource documentSource;

	private Configuration jsonPathConfig = Configuration.builder().build();

	private Duration responseTimeout = DEFAULT_RESPONSE_DURATION;


	public AbstractGraphQlTesterBuilder() {
		this.documentSource = initDocumentSource();
	}

	private static DocumentSource initDocumentSource() {
		return new ResourceDocumentSource(
				Collections.singletonList(new ClassPathResource("graphql-test/")),
				ResourceDocumentSource.FILE_EXTENSIONS);
	}


	@Override
	public B errorFilter(Predicate<ResponseError> predicate) {
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
	 * with {@link AbstractDelegatingGraphQlTester}.
	 */
	protected GraphQlTester buildGraphQlTester(GraphQlTransport transport) {

		if (jackson2Present) {
			configureJsonPathConfig(Jackson2Configurer::configure);
		}

		return new DefaultGraphQlTester(transport, this.errorFilter,
				this.jsonPathConfig, this.documentSource, this.responseTimeout);
	}

	/**
	 * Subclasses call this from {@link #build()} to obtain a {@code Consumer} to
	 * initialize new builder instances with, based on "this" builder.
	 */
	protected Consumer<AbstractGraphQlTesterBuilder<?>> getBuilderInitializer() {
		return builder -> {
			if (this.errorFilter != null) {
				builder.errorFilter(this.errorFilter);
			}
			builder.documentSource(this.documentSource);
			builder.configureJsonPathConfig(config -> this.jsonPathConfig);
			builder.responseTimeout(this.responseTimeout);
		};
	}

	/**
	 * For cases where the Tester needs the {@link GraphQlTransport}, we can't use
	 * transports directly since they are package private, but we can adapt the corresponding
	 * {@link GraphQlClient} and adapt it to {@code GraphQlTransport}.
	 */
	protected static GraphQlTransport asTransport(GraphQlClient client) {
		return new GraphQlTransport() {

			@Override
			public Mono<GraphQlResponse> execute(GraphQlRequest request) {
				return client
						.document(request.getDocument())
						.operationName(request.getOperationName())
						.variables(request.getVariables())
						.execute()
						.cast(GraphQlResponse.class);
			}

			@Override
			public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
				return client
						.document(request.getDocument())
						.operationName(request.getOperationName())
						.variables(request.getVariables())
						.executeSubscription()
						.cast(GraphQlResponse.class);
			}

            @Override
            public Mono<GraphQlResponse> executeFileUpload(GraphQlRequest request) {
                throw new UnsupportedOperationException("File upload is not supported");
            }
        };
	}


	private static class Jackson2Configurer {

		private static final Class<?> defaultJsonProviderType;

		private static final Class<?> defaultMappingProviderType;

		static {
			Configuration config = Configuration.defaultConfiguration();
			defaultJsonProviderType = config.jsonProvider().getClass();
			defaultMappingProviderType = config.mappingProvider().getClass();
		}

		// GraphQlTransport returns ExecutionResult with JSON parsed to Map/List,
		// but we still need JsonProvider for matchesJson(String)

		static Configuration configure(Configuration config) {
			if (isDefault(config.jsonProvider(), defaultJsonProviderType)) {
				config = config.jsonProvider(new JacksonJsonProvider());
			}
			if (isDefault(config.mappingProvider(), defaultMappingProviderType)) {
				config = config.mappingProvider(new JacksonMappingProvider());
			}
			return config;
		}

		private static <T> boolean isDefault(@Nullable T provider, Class<? extends T> defaultProviderType) {
			return (provider == null || defaultProviderType.isInstance(provider));
		}

	}

}
