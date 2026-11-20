/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;


import java.util.function.Consumer;

import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.util.Assert;


/**
 * Default {@link GraphQlTester.Builder} with a given, externally prepared transport.
 *
 * @author Rossen Stoyanchev
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
	private static final class DefaultTransportGraphQlTester extends AbstractDelegatingGraphQlTester {

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
