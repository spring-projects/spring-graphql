/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import org.springframework.http.MediaType;

/**
 * Constants for well-known GraphQL media types.
 * @author Brian Clozel
 * @since 1.4.0
 */
public abstract class MediaTypes {

	/**
	 * Standard media type for GraphQL responses over the HTTP protocol.
	 * @see <a href="https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json">
	 *     GraphQL over HTTP specification</a>
	 */
	public static final MediaType APPLICATION_GRAPHQL_RESPONSE =
			MediaType.parseMediaType("application/graphql-response+json");

}
