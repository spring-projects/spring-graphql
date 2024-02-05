/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.data.federation;


import java.util.Map;

import org.springframework.graphql.data.method.HandlerMethod;

/**
 * Specialization of {@link RepresentationException} that indicates a resolver
 * returned {@code null} or completed empty.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
@SuppressWarnings("serial")
public class RepresentationNotResolvedException extends RepresentationException {

	public RepresentationNotResolvedException(Map<String, Object> representation, HandlerMethod handlerMethod) {
		super(representation, handlerMethod, "Entity fetcher returned null or completed empty");
	}

}
