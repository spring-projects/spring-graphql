/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.federation;


import java.util.Map;

import org.springframework.graphql.data.method.HandlerMethod;

/**
 * Specialization of {@link RepresentationException} that indicates a resolver
 * returned {@code null} or completed empty.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
@SuppressWarnings("serial")
public class RepresentationNotResolvedException extends RepresentationException {

	public RepresentationNotResolvedException(Map<String, Object> representation, HandlerMethod handlerMethod) {
		super(representation, handlerMethod, "Entity fetcher returned null or completed empty");
	}

}
