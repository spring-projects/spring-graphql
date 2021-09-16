/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.boot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.NestedRuntimeException;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * {@link InvalidSchemaLocationsException} thrown when no schema file could be found in the provided locations.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class InvalidSchemaLocationsException extends NestedRuntimeException {

	private final List<SchemaLocation> schemaLocations;

	public InvalidSchemaLocationsException(List<String> locations, ResourcePatternResolver resolver) {
		this(locations, resolver, null);
	}

	public InvalidSchemaLocationsException(List<String> locations, ResourcePatternResolver resolver, Throwable cause) {
		super("No schema file could be found in the provided locations.", cause);
		List<SchemaLocation> providedLocations = new ArrayList<>();
		for (String location : locations) {
			try {
				String uri = resolver.getResource(location).getURI().toASCIIString();
				providedLocations.add(new SchemaLocation(location, uri));
			}
			catch (IOException ex) {
				providedLocations.add(new SchemaLocation(location, ""));
			}
		}
		this.schemaLocations = Collections.unmodifiableList(providedLocations);
	}

	/**
	 * Return the list of provided locations where to look for schemas.
	 */
	public List<SchemaLocation> getSchemaLocations() {
		return this.schemaLocations;
	}

	/**
	 * The location where to look for schemas.
	 */
	public static class SchemaLocation {

		private final String location;

		private final String uri;

		SchemaLocation(String location, String uri) {
			this.location = location;
			this.uri = uri;
		}

		/**
		 * Return the location String to be resolved by a {@link ResourcePatternResolver}.
		 */
		public String getLocation() {
			return this.location;
		}

		/**
		 * Return the resolved URI String for this location, an empty String if resolution failed.
		 */
		public String getUri() {
			return this.uri;
		}
	}
}
