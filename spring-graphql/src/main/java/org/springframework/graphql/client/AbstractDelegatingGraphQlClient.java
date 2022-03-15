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


import org.springframework.util.Assert;

/**
 * Base class for {@link GraphQlClient} extensions that assist with building an
 * underlying transport, but otherwise delegate to the default
 * {@link GraphQlClient} implementation to execute requests.
 *
 * <p>Subclasses must implement {@link GraphQlClient#mutate()} to return a
 * builder for the specific {@code GraphQlClient} extension.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see AbstractGraphQlClientBuilder
 */
public abstract class AbstractDelegatingGraphQlClient implements GraphQlClient {

	private final GraphQlClient graphQlClient;


	protected AbstractDelegatingGraphQlClient(GraphQlClient graphQlClient) {
		Assert.notNull(graphQlClient, "GraphQlClient is required");
		this.graphQlClient = graphQlClient;
	}


	public RequestSpec document(String document) {
		return this.graphQlClient.document(document);
	}

	public RequestSpec documentName(String name) {
		return this.graphQlClient.documentName(name);
	}

}
