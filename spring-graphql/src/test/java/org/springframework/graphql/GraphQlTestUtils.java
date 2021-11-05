/*
 * Copyright 2002-2021 the original author or authors.
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

import java.nio.charset.StandardCharsets;

import graphql.schema.DataFetcher;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Utility methods for GraphQL tests.
 */
public abstract class GraphQlTestUtils {

	/**
	 * Initialize a {@link GraphQlSource.Builder} with a single {@link DataFetcher}.
	 *
	 * @param schema either String content or a {@link Resource}.
	 * @param typeName the parent type name (Query, Mutation, or Subscription).
	 * @param fieldName the name of the operation
	 * @param fetcher the fetcher to use
	 *
	 * @return the created builder
	 */
	public static GraphQlSource.Builder graphQlSource(
			Object schema, String typeName, String fieldName, DataFetcher<?> fetcher) {

		return graphQlSource(schema,
				wiring -> wiring.type(typeName, builder -> builder.dataFetcher(fieldName, fetcher)));
	}

	/**
	 * Initialize a {@link GraphQlSource.Builder} with a {@link RuntimeWiringConfigurer},
	 * which may be useful for multiple {@link DataFetcher} registrations, or for a
	 * built-in implementation (e.g. for annotated handler methods).
	 *
	 * @param schema either String content or a {@link Resource}.
	 * @param configurer the configurer to apply to the RuntimeWiring
	 *
	 * @return the created builder
	 */
	public static GraphQlSource.Builder graphQlSource(Object schema, RuntimeWiringConfigurer configurer) {
		Resource schemaResource = (schema instanceof String ?
				new ByteArrayResource(((String) schema).getBytes(StandardCharsets.UTF_8)) :
				(Resource) schema);

		return GraphQlSource.builder()
				.schemaResources(schemaResource)
				.configureRuntimeWiring(configurer);
	}

}
