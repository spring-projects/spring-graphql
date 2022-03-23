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

import org.springframework.util.Assert;


/**
 * GraphQL client with a given, externally prepared transport.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class GenericGraphQlClient extends AbstractDelegatingGraphQlClient {

	private final GraphQlTransport transport;

	private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;


	GenericGraphQlClient(
			GraphQlClient graphQlClient, GraphQlTransport transport,
			Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

		super(graphQlClient);
		Assert.notNull(transport, "GraphQlTransport is required");
		Assert.notNull(builderInitializer, "'builderInitializer' is required");
		this.transport = transport;
		this.builderInitializer = builderInitializer;
	}


	@Override
	public Builder mutate() {
		Builder builder = new Builder(this.transport);
		this.builderInitializer.accept(builder);
		return builder;
	}


	/**
	 * Default {@link GraphQlClient.Builder} with a given transport.
	 */
	static final class Builder extends AbstractGraphQlClientBuilder<Builder> {

		private final GraphQlTransport transport;

		Builder(GraphQlTransport transport) {
			Assert.notNull(transport, "GraphQlTransport is required");
			this.transport = transport;
		}

		@Override
		public GraphQlClient build() {
			GraphQlClient client = buildGraphQlClient(this.transport);
			return new GenericGraphQlClient(client, this.transport, getBuilderInitializer());
		}

	}

}
