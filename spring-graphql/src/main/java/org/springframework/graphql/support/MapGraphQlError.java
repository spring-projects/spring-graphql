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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.language.SourceLocation;

import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Implementation of {@link GraphQLError} backed by a {@link Map}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
final class MapGraphQlError implements GraphQLError {

	private final Map<String, Object> errorMap;

	private final List<SourceLocation> locations;


	MapGraphQlError(Map<String, Object> errorMap) {
		Assert.notNull(errorMap, "'errorMap' is required");
		this.errorMap = errorMap;
		this.locations = initLocations(errorMap);
	}

	@SuppressWarnings("unchecked")
	private static List<SourceLocation> initLocations(Map<String, Object> errorMap) {
		List<Map<String, Object>> locations = (List<Map<String, Object>>) errorMap.get("locations");
		if (locations == null) {
			return Collections.emptyList();
		}
		return locations.stream()
				.map(map -> new SourceLocation(
						(int) map.getOrDefault("line", 0),
						(int) map.getOrDefault("column", 0),
						(String) map.get("sourceName")))
				.collect(Collectors.toList());
	}


	@Override
	@Nullable
	public String getMessage() {
		return (String) errorMap.get("message");
	}

	@Override
	public List<SourceLocation> getLocations() {
		return this.locations;
	}

	@Override
	@Nullable
	public ErrorClassification getErrorType() {
		// Attempt the reverse of how errorType is serialized in GraphqlErrorHelper.toSpecification.
		// However, we can only do that for ErrorClassification enums that we know of.
		String value = (getExtensions() != null ? (String) getExtensions().get("classification") : null);
		if (value != null) {
			try {
				return graphql.ErrorType.valueOf(value);
			}
			catch (IllegalArgumentException ex) {
				// ignore
			}
			try {
				return ErrorType.valueOf(value);
			}
			catch (IllegalArgumentException ex) {
				// ignore
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	@Nullable
	public List<Object> getPath() {
		return (List<Object>) this.errorMap.get("path");
	}

	@SuppressWarnings("unchecked")
	@Override
	@Nullable
	public Map<String, Object> getExtensions() {
		return (Map<String, Object>) this.errorMap.get("extensions");
	}

	@Override
	public Map<String, Object> toSpecification() {
		return GraphqlErrorHelper.toSpecification(this);
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object other) {
		return GraphqlErrorHelper.equals(this, other);
	}

	@Override
	public int hashCode() {
		return GraphqlErrorHelper.hashCode(this);
	}

	@Override
	public String toString() {
		return toSpecification().toString();
	}

}
