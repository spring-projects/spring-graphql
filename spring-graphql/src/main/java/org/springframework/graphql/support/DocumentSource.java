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
