/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import graphql.ExecutionInput;
import org.jspecify.annotations.Nullable;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.util.Assert;


/**
 * Default {@link ExecutionGraphQlServiceTester.Builder} implementation that
 * wraps an {@code ExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 */
final class DefaultExecutionGraphQlServiceTesterBuilder
		extends AbstractGraphQlTesterBuilder<DefaultExecutionGraphQlServiceTesterBuilder>
		implements ExecutionGraphQlServiceTester.Builder<DefaultExecutionGraphQlServiceTesterBuilder> {

	private final ExecutionGraphQlService service;

	private final List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers;

	private @Nullable Encoder<?> encoder;

	private @Nullable Decoder<?> decoder;


	DefaultExecutionGraphQlServiceTesterBuilder(ExecutionGraphQlService service) {
		Assert.notNull(service, "GraphQlService is required");
		this.service = service;
		this.executionInputConfigurers = new ArrayList<>();
	}

	DefaultExecutionGraphQlServiceTesterBuilder(GraphQlServiceGraphQlTransport transport) {
		this.service = transport.getGraphQlService();
		this.executionInputConfigurers = new ArrayList<>(transport.getExecutionInputConfigurers());
	}

	@Override
	public DefaultExecutionGraphQlServiceTesterBuilder configureExecutionInput(
			BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer) {

		this.executionInputConfigurers.add(configurer);
		return this;
	}

	@Override
	public DefaultExecutionGraphQlServiceTesterBuilder encoder(Encoder<?> encoder) {
		this.encoder = encoder;
		return this;
	}

	@Override
	public DefaultExecutionGraphQlServiceTesterBuilder decoder(Decoder<?> decoder) {
		this.decoder = decoder;
		return this;
	}

	@Override
	public ExecutionGraphQlServiceTester build() {
		registerJsonPathMappingProvider();
		GraphQlServiceGraphQlTransport transport = createTransport();
		GraphQlTester tester = super.buildGraphQlTester(transport);
		return new DefaultExecutionGraphQlServiceTester(tester, transport, getBuilderInitializer());
	}

	private void registerJsonPathMappingProvider() {
		if (this.encoder != null && this.decoder != null) {
			configureJsonPathConfig((config) -> {
				EncoderDecoderMappingProvider provider = new EncoderDecoderMappingProvider(
						Collections.singletonList(this.encoder), Collections.singletonList(this.decoder));
				return config.mappingProvider(provider);
			});
		}
	}

	private GraphQlServiceGraphQlTransport createTransport() {
		return new GraphQlServiceGraphQlTransport(this.service, this.executionInputConfigurers);
	}


	/**
	 * Default {@link ExecutionGraphQlServiceTester} implementation.
	 */
	private static final class DefaultExecutionGraphQlServiceTester
			extends AbstractDelegatingGraphQlTester implements ExecutionGraphQlServiceTester {

		private final GraphQlServiceGraphQlTransport transport;

		private final Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer;

		private DefaultExecutionGraphQlServiceTester(GraphQlTester tester, GraphQlServiceGraphQlTransport transport,
				Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer) {

			super(tester);

			Assert.notNull(transport, "GraphQlServiceTransport is required");
			Assert.notNull(builderInitializer, "`builderInitializer` is required");

			this.transport = transport;
			this.builderInitializer = builderInitializer;
		}

		@Override
		public DefaultExecutionGraphQlServiceTesterBuilder mutate() {
			DefaultExecutionGraphQlServiceTesterBuilder builder = new DefaultExecutionGraphQlServiceTesterBuilder(this.transport);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}
