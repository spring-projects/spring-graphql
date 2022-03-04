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
