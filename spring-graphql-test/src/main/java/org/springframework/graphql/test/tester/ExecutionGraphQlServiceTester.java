/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;

import java.util.function.BiFunction;

import graphql.ExecutionInput;

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
	 * @param service the GraphQL service to use
	 */
	static ExecutionGraphQlServiceTester create(ExecutionGraphQlService service) {
		return builder(service).build();
	}

	/**
	 * Return a builder for {@link ExecutionGraphQlServiceTester}.
	 * @param service the GraphQL service to use
	 */
	static ExecutionGraphQlServiceTester.Builder<?> builder(ExecutionGraphQlService service) {
		return new DefaultExecutionGraphQlServiceTesterBuilder(service);
	}


	/**
	 * Default {@link ExecutionGraphQlServiceTester.Builder} implementation.
	 * @param <B> the type of builder
	 */
	interface Builder<B extends Builder<B>> extends GraphQlTester.Builder<B> {

		/**
		 * Provide a {@code BiFunction} to help initialize the
		 * {@link ExecutionInput} with.
		 * @param configurer the function that initializes the execution input
		 * @since 1.1.2
		 * @see org.springframework.graphql.ExecutionGraphQlRequest#configureExecutionInput(BiFunction)
		 */
		B configureExecutionInput(BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer);

		/**
		 * Configure the JSON encoder to use for mapping response data to
		 * higher level objects.
		 * @param encoder the JSON encoder to use
		 */
		B encoder(Encoder<?> encoder);

		/**
		 * Configure the JSON decoder to use for mapping response data to
		 * higher level objects.
		 * @param decoder the JSON decoder to use
		 */
		B decoder(Decoder<?> decoder);

		/**
		 * Build a {@link ExecutionGraphQlServiceTester} instance.
		 */
		@Override
		ExecutionGraphQlServiceTester build();

	}

}
