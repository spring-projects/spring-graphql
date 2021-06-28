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

package org.springframework.graphql.test.tester;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;

/**
 * {@link GraphQLError} with setters to use for deserialization.
 *
 * @author Rossen Stoyanchev
 */
class TestGraphQlError implements GraphQLError {

	private String message;

	private List<SourceLocation> locations;

	private ErrorClassification errorType;

	private List<Object> path;

	private Map<String, Object> extensions;

	private boolean expected;

	void setMessage(String message) {
		this.message = message;
	}

	@Override
	public String getMessage() {
		return this.message;
	}

	void setLocations(List<TestSourceLocation> locations) {
		this.locations = TestSourceLocation.toSourceLocations(locations);
	}

	@Override
	public List<SourceLocation> getLocations() {
		return this.locations;
	}

	void setErrorType(ErrorClassification errorType) {
		this.errorType = errorType;
	}

	@Override
	public ErrorClassification getErrorType() {
		return this.errorType;
	}

	void setPath(List<Object> path) {
		this.path = path;
	}

	@Override
	public List<Object> getPath() {
		return this.path;
	}

	void setExtensions(Map<String, Object> extensions) {
		this.extensions = extensions;
	}

	@Override
	public Map<String, Object> getExtensions() {
		return this.extensions;
	}

	/**
	 * Whether the error is marked as filtered out as expected.
	 * @return whether the error is marked as expected
	 */
	boolean isExpected() {
		return this.expected;
	}

	@Override
	public Map<String, Object> toSpecification() {
		GraphqlErrorBuilder builder = GraphqlErrorBuilder.newError();
		if (this.message != null) {
			builder.message(this.message);
		}
		if (this.locations != null) {
			this.locations.forEach(builder::location);
		}
		if (this.path != null) {
			builder.path(ResultPath.fromList(this.path));
		}
		if (this.extensions != null) {
			builder.extensions(this.extensions);
		}
		return builder.build().toSpecification();
	}

	/**
	 * Mark this error as expected if it matches the predicate.
	 * @param predicate the error predicate
	 */
	void applyErrorFilterPredicate(Predicate<GraphQLError> predicate) {
		this.expected |= predicate.test(this);
	}

	@Override
	public String toString() {
		return toSpecification().toString();
	}

	private static final class TestSourceLocation {

		private int line;

		private int column;

		private String sourceName;

		void setLine(int line) {
			this.line = line;
		}

		int getLine() {
			return this.line;
		}

		void setColumn(int column) {
			this.column = column;
		}

		int getColumn() {
			return this.column;
		}

		void setSourceName(String sourceName) {
			this.sourceName = sourceName;
		}

		String getSourceName() {
			return this.sourceName;
		}

		static List<SourceLocation> toSourceLocations(List<TestSourceLocation> locations) {
			return locations.stream()
					.map((location) -> new SourceLocation(location.line, location.column, location.sourceName))
					.collect(Collectors.toList());
		}

	}

}
