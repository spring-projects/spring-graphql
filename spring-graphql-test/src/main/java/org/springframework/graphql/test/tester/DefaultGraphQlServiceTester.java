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

import org.springframework.graphql.GraphQlService;
import org.springframework.util.Assert;


/**
 * Default {@link GraphQlServiceTester} that uses a {@link GraphQlService} for
 * request execution.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultGraphQlServiceTester extends AbstractDelegatingGraphQlTester implements GraphQlServiceTester {

	private final GraphQlServiceTransport transport;

	private final Consumer<GraphQlTester.Builder<?>> builderInitializer;


	DefaultGraphQlServiceTester(GraphQlTester tester, GraphQlServiceTransport transport,
			Consumer<GraphQlTester.Builder<?>> builderInitializer) {

		super(tester);

		Assert.notNull(transport, "GraphQlServiceTransport is required");
		Assert.notNull(builderInitializer, "`builderInitializer` is required");

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
	 * Default {@link GraphQlServiceTester.Builder} implementation.
	 */
	static class Builder<B extends Builder<B>> extends AbstractGraphQlTesterBuilder<B>
			implements GraphQlServiceTester.Builder<B> {

		private final GraphQlService service;

		Builder(GraphQlService service) {
			Assert.notNull(service, "GraphQlService is required");
			this.service = service;
		}

		Builder(GraphQlServiceTransport transport) {
			this.service = transport.getGraphQlService();
		}

		@Override
		public GraphQlServiceTester build() {
			GraphQlServiceTransport transport = new GraphQlServiceTransport(this.service);
			GraphQlTester tester = super.buildGraphQlTester(transport);
			return new DefaultGraphQlServiceTester(tester, transport, getBuilderInitializer());
		}

	}

}
