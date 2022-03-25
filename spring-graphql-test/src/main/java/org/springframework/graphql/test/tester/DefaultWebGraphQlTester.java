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


import java.net.URI;
import java.util.Arrays;
import java.util.function.Consumer;

import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Default {@link WebGraphQlTester} that uses {@link WebGraphQlHandler} for
 * request execution.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultWebGraphQlTester extends AbstractDelegatingGraphQlTester implements WebGraphQlTester {

	private final WebGraphQlHandlerGraphQlTransport transport;

	private final Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer;


	DefaultWebGraphQlTester(GraphQlTester tester, WebGraphQlHandlerGraphQlTransport transport,
			Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer) {

		super(tester);
		this.transport = transport;
		this.builderInitializer = builderInitializer;
	}


	@Override
	public Builder<?> mutate() {
		Builder<?> builder = new Builder<>(this.transport);
		this.builderInitializer.accept(builder);
		return builder;
	}


	/**
	 * Base builder implementation for all Web transport extensions.
	 */
	static class Builder<B extends Builder<B>> extends AbstractGraphQlTesterBuilder<B>
			implements WebGraphQlTester.Builder<B> {

		private URI url = URI.create("");

		private final HttpHeaders headers = new HttpHeaders();

		private final WebGraphQlHandler handler;

		private CodecConfigurer codecConfigurer = ClientCodecConfigurer.create();

		Builder(WebGraphQlHandler handler) {
			Assert.notNull(handler, "WebGraphQlHandler is required");
			this.handler = handler;
		}

		Builder(WebGraphQlHandlerGraphQlTransport transport) {
			this.url = transport.getUrl();
			this.headers.putAll(transport.getHeaders());
			this.handler = transport.getGraphQlHandler();
			this.codecConfigurer = transport.getCodecConfigurer();
		}

		@Override
		public B url(String url) {
			return url(new DefaultUriBuilderFactory().uriString(url).build());
		}

		@Override
		public B url(URI url) {
			this.url = url;
			return self();
		}

		@Override
		public B header(String name, String... values) {
			this.headers.put(name, Arrays.asList(values));
			return self();
		}

		@Override
		public B headers(Consumer<HttpHeaders> headersConsumer) {
			headersConsumer.accept(this.headers);
			return self();
		}

		@Override
		public B codecConfigurer(Consumer<CodecConfigurer> codecConfigurerConsumer) {
			codecConfigurerConsumer.accept(this.codecConfigurer);
			return self();
		}

		@SuppressWarnings("unchecked")
		protected <T extends B> T self() {
			return (T) this;
		}

		@Override
		public WebGraphQlTester build() {

			registerJsonPathMappingProvider();

			WebGraphQlHandlerGraphQlTransport transport =
					new WebGraphQlHandlerGraphQlTransport(this.url, this.headers, this.handler, this.codecConfigurer);

			GraphQlTester tester = super.buildGraphQlTester(transport);
			return new DefaultWebGraphQlTester(tester, transport, getBuilderInitializer());
		}

		private void registerJsonPathMappingProvider() {
			configureJsonPathConfig(jsonPathConfig -> {
				EncoderDecoderMappingProvider provider = new EncoderDecoderMappingProvider(this.codecConfigurer);
				return jsonPathConfig.mappingProvider(provider);
			});
		}

	}

}
