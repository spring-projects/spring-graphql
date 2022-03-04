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

import org.springframework.graphql.GraphQlService;

/**
 * {@link GraphQlTester} that executes requests through a {@link GraphQlService}
 * Use it for server-side tests, without a client.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlServiceTester extends GraphQlTester {


	@Override
	Builder<?> mutate();


	/**
	 * Create a {@link GraphQlServiceTester} instance.
	 */
	static GraphQlServiceTester create(GraphQlService service) {
		return builder(service).build();
	}

	/**
	 * Return a builder for {@link GraphQlServiceTester}.
	 */
	static GraphQlServiceTester.Builder<?> builder(GraphQlService service) {
		return new DefaultGraphQlServiceTester.Builder<>(service);
	}


	/**
	 * Default {@link GraphQlServiceTester.Builder} implementation.
	 */
	interface Builder<B extends Builder<B>> extends GraphQlTester.Builder<B> {

		/**
		 * Build a {@link GraphQlServiceTester} instance.
		 */
		@Override
		GraphQlServiceTester build();

	}

}
