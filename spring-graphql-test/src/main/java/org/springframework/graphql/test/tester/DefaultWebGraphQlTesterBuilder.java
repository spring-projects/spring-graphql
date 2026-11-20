/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
 * Default implementation for {@link WebGraphQlTester} that initializes a
 * {@link WebGraphQlHandler} for request execution.
 *
 * @author Rossen Stoyanchev
 */
final class DefaultWebGraphQlTesterBuilder
		extends AbstractGraphQlTesterBuilder<DefaultWebGraphQlTesterBuilder>
		implements WebGraphQlTester.Builder<DefaultWebGraphQlTesterBuilder> {

	private URI url = URI.create("");

	private final HttpHeaders headers = new HttpHeaders();

	private final WebGraphQlHandler handler;

	private CodecConfigurer codecConfigurer = ClientCodecConfigurer.create();


	DefaultWebGraphQlTesterBuilder(WebGraphQlHandler handler) {
		Assert.notNull(handler, "WebGraphQlHandler is required");
		this.handler = handler;
	}

	DefaultWebGraphQlTesterBuilder(WebGraphQlHandlerGraphQlTransport transport) {
		this.url = transport.getUrl();
		this.headers.putAll(transport.getHeaders());
		this.handler = transport.getGraphQlHandler();
		this.codecConfigurer = transport.getCodecConfigurer();
	}


	@Override
	public DefaultWebGraphQlTesterBuilder url(String url) {
		return url(new DefaultUriBuilderFactory().uriString(url).build());
	}

	@Override
	public DefaultWebGraphQlTesterBuilder url(URI url) {
		this.url = url;
		return this;
	}

	@Override
	public DefaultWebGraphQlTesterBuilder header(String name, String... values) {
		this.headers.put(name, Arrays.asList(values));
		return this;
	}

	@Override
	public DefaultWebGraphQlTesterBuilder headers(Consumer<HttpHeaders> headersConsumer) {
		headersConsumer.accept(this.headers);
		return this;
	}

	@Override
	public DefaultWebGraphQlTesterBuilder codecConfigurer(Consumer<CodecConfigurer> codecConfigurerConsumer) {
		codecConfigurerConsumer.accept(this.codecConfigurer);
		return this;
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
		configureJsonPathConfig((jsonPathConfig) -> {
			EncoderDecoderMappingProvider provider = new EncoderDecoderMappingProvider(this.codecConfigurer);
			return jsonPathConfig.mappingProvider(provider);
		});
	}


	/**
	 * Default {@link WebGraphQlTester} implementation.
	 */
	private static final class DefaultWebGraphQlTester extends AbstractDelegatingGraphQlTester implements WebGraphQlTester {

		private final WebGraphQlHandlerGraphQlTransport transport;

		private final Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer;

		private DefaultWebGraphQlTester(GraphQlTester tester, WebGraphQlHandlerGraphQlTransport transport,
				Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer) {

			super(tester);
			this.transport = transport;
			this.builderInitializer = builderInitializer;
		}

		@Override
		public DefaultWebGraphQlTesterBuilder mutate() {
			DefaultWebGraphQlTesterBuilder builder = new DefaultWebGraphQlTesterBuilder(this.transport);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}
