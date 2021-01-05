/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.boot.graphql;

import org.springframework.core.io.Resource;

/**
 * Exception thrown when no GraphQL schema is available.
 */
public class MissingGraphQLSchemaException extends RuntimeException {

	private final Resource schemaResource;

	public MissingGraphQLSchemaException(Throwable cause, Resource schemaResource) {
		super(cause);
		this.schemaResource = schemaResource;
	}

	MissingGraphQLSchemaException(Resource schemaResource) {
		super("Schema resource " + schemaResource.toString() + " does not exist");
		this.schemaResource = schemaResource;
	}

	public Resource getSchemaResource() {
		return this.schemaResource;
	}
}
