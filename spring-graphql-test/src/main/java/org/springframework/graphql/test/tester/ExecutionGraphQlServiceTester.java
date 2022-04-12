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

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.ExecutionGraphQlService;

/**
 * {@link GraphQlTester} that executes requests through an
 * {@link ExecutionGraphQlService} on the server side, without a client.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ExecutionGraphQlServiceTester extends GraphQlTester {


	@Override
	Builder<?> mutate();


	/**
	 * Create a {@link ExecutionGraphQlServiceTester} instance.
	 */
	static ExecutionGraphQlServiceTester create(ExecutionGraphQlService service) {
		return builder(service).build();
	}

	/**
	 * Return a builder for {@link ExecutionGraphQlServiceTester}.
	 */
	static ExecutionGraphQlServiceTester.Builder<?> builder(ExecutionGraphQlService service) {
		return new DefaultExecutionGraphQlServiceTesterBuilder(service);
	}


	/**
	 * Default {@link ExecutionGraphQlServiceTester.Builder} implementation.
	 */
	interface Builder<B extends Builder<B>> extends GraphQlTester.Builder<B> {

		/**
		 * Configure the JSON encoder to use for mapping response data to
		 * higher level objects.
		 */
		B encoder(Encoder<?> encoder);

		/**
		 * Configure the JSON decoder to use for mapping response data to
		 * higher level objects.
		 */
		B decoder(Decoder<?> decoder);

		/**
		 * Build a {@link ExecutionGraphQlServiceTester} instance.
		 */
		@Override
		ExecutionGraphQlServiceTester build();

	}

}
