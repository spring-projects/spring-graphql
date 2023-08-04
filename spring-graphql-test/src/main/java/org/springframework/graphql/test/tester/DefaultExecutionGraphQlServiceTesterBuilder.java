/*
 * Copyright 2002-2023 the original author or authors.
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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import graphql.ExecutionInput;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;


/**
 * Default {@link ExecutionGraphQlServiceTester.Builder} implementation that
 * wraps an {@code ExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultExecutionGraphQlServiceTesterBuilder
		extends AbstractGraphQlTesterBuilder<DefaultExecutionGraphQlServiceTesterBuilder>
		implements ExecutionGraphQlServiceTester.Builder<DefaultExecutionGraphQlServiceTesterBuilder> {

	private final ExecutionGraphQlService service;

	private final List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers;

	@Nullable
	private Encoder<?> encoder;

	@Nullable
	private Decoder<?> decoder;


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
			configureJsonPathConfig(config -> {
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
