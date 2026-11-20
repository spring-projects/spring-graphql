/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;


/**
 * Base class for extensions of {@link GraphQlTester} that mainly assist with
 * building the underlying transport, but otherwise delegate to the default
 * {@link GraphQlTester} implementation for actual request execution.
 *
 * <p>Subclasses must implement {@link GraphQlTester#mutate()} to allow mutation
 * of both {@code GraphQlTester} and {@code GraphQlTransport} configuration.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see AbstractGraphQlTesterBuilder
 */
public abstract class AbstractDelegatingGraphQlTester implements GraphQlTester {

	private final GraphQlTester delegate;


	protected AbstractDelegatingGraphQlTester(GraphQlTester delegate) {
		this.delegate = delegate;
	}


	@Override
	public Request<?> document(String document) {
		return this.delegate.document(document);
	}

	@Override
	public Request<?> documentName(String documentName) {
		return this.delegate.documentName(documentName);
	}

}
