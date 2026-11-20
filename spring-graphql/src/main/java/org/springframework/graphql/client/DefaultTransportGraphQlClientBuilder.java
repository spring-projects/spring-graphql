/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;


import java.util.function.Consumer;

import org.springframework.util.Assert;


/**
 * Default {@link GraphQlClient.Builder} with a given, externally, prepared transport.
 *
 * @author Rossen Stoyanchev
 */
final class DefaultTransportGraphQlClientBuilder
		extends AbstractGraphQlClientBuilder<DefaultTransportGraphQlClientBuilder> {

	private final GraphQlTransport transport;


	DefaultTransportGraphQlClientBuilder(GraphQlTransport transport) {
		Assert.notNull(transport, "GraphQlTransport is required");
		this.transport = transport;
	}


	@Override
	public GraphQlClient build() {
		GraphQlClient client = buildGraphQlClient(this.transport);
		return new DefaultTransportGraphQlClient(client, this.transport, getBuilderInitializer());
	}


	/**
	 * Default GraphQL client with a given transport.
	 */
	private static class DefaultTransportGraphQlClient extends AbstractDelegatingGraphQlClient {

		private final GraphQlTransport transport;

		private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;


		DefaultTransportGraphQlClient(
				GraphQlClient graphQlClient, GraphQlTransport transport,
				Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

			super(graphQlClient);
			Assert.notNull(transport, "GraphQlTransport is required");
			Assert.notNull(builderInitializer, "'builderInitializer' is required");
			this.transport = transport;
			this.builderInitializer = builderInitializer;
		}


		@Override
		public DefaultTransportGraphQlClientBuilder mutate() {
			DefaultTransportGraphQlClientBuilder builder = new DefaultTransportGraphQlClientBuilder(this.transport);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}
