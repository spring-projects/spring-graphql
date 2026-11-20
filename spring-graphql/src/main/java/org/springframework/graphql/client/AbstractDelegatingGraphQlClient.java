/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
