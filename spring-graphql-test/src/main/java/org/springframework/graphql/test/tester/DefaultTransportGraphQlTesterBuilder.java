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


import java.util.function.Consumer;

import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.util.Assert;


/**
 * Default {@link GraphQlTester.Builder} with a given, externally prepared transport.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultTransportGraphQlTesterBuilder
		extends AbstractGraphQlTesterBuilder<DefaultTransportGraphQlTesterBuilder> {

	private final GraphQlTransport transport;


	DefaultTransportGraphQlTesterBuilder(GraphQlTransport transport) {
		this.transport = transport;
	}


	@Override
	public GraphQlTester build() {
		GraphQlTester tester = super.buildGraphQlTester(this.transport);
		return new DefaultTransportGraphQlTester(tester, this.transport, getBuilderInitializer());
	}


	/**
	 * {@link GraphQlTester} with a given transport.
	 */
	private static class DefaultTransportGraphQlTester extends AbstractDelegatingGraphQlTester {

		private final GraphQlTransport transport;

		private final Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer;

		private DefaultTransportGraphQlTester(
				GraphQlTester delegate, GraphQlTransport transport,
				Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer) {

			super(delegate);
			Assert.notNull(transport, "GraphQlTransport is required");
			Assert.notNull(builderInitializer, "'builderInitializer' is required");
			this.transport = transport;
			this.builderInitializer = builderInitializer;
		}

		@Override
		public DefaultTransportGraphQlTesterBuilder mutate() {
			DefaultTransportGraphQlTesterBuilder builder = new DefaultTransportGraphQlTesterBuilder(this.transport);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}
