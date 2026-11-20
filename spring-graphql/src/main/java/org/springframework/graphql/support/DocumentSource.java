/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.support;

import reactor.core.publisher.Mono;

/**
 * Strategy to locate a GraphQL document by a name.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface DocumentSource {

	/**
	 * Return the document that matches the given name.
	 * @param name the name to use for the lookup
	 * @return {@code Mono} that completes either with the document content or
	 * with an error, but never empty.
	 */
	Mono<String> getDocument(String name);

}
