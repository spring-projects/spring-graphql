/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import org.springframework.graphql.support.DefaultGraphQlRequest;

/**
 * Default implementation of {@link ClientGraphQlRequest}.
 *
 * @author Rossen Stoyanchev
 */
final class DefaultClientGraphQlRequest extends DefaultGraphQlRequest implements ClientGraphQlRequest {

	private final Map<String, Object> attributes = new ConcurrentHashMap<>();


	DefaultClientGraphQlRequest(
			String document, @Nullable String operationName,
			Map<String, @Nullable Object> variables, Map<String, Object> extensions,
			Map<String, Object> attributes) {

		super(document, operationName, variables, extensions);
		this.attributes.putAll(attributes);
	}


	@Override
	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

}
