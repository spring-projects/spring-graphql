/*
 * Copyright 2020-present the original author or authors.
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
