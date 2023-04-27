/*
 * Copyright 2020-2023 the original author or authors.
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

package org.springframework.graphql.execution;


import java.util.Map;
import java.util.Set;

import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;

import org.springframework.util.MultiValueMap;


/**
 * The report produced as a result of schema mappings inspection.
 * @param unmappedFields map with type names as keys, and unmapped field names as values
 * @param unmappedDataFetchers map with unmapped {@code DataFetcher}s and their field coordinates
 * @param skippedTypes the names of types skipped by the inspection
 *
 * @since 1.2.0
 */
public record SchemaMappingReport(
		MultiValueMap<String, String> unmappedFields,
		Map<FieldCoordinates, DataFetcher<?>> unmappedDataFetchers,
		Set<String> skippedTypes) {

	@Override
	public String toString() {
		return "GraphQL schema inspection:\n" +
				"\tUnmapped fields: " + this.unmappedFields + "\n" +
				"\tUnmapped DataFetcher registrations: " + this.unmappedDataFetchers + "\n" +
				"\tSkipped types: " + this.skippedTypes;
	}

}
