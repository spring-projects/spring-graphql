/*
 * Copyright 2020-2020 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "spring.graphql")
public class GraphQLProperties {

	/**
	 * Location of the GraphQL schema file.
	 */
	private String schemaLocation = "classpath:schema.graphqls";

	/**
	 * Path of the GraphQL HTTP endpoint.
	 */
	private String path = "/graphql";

	/**
	 * spring data repo info
	 * key:graphql query name
	 * value: repo properties
	 */
	private Map<String, RepositoryProperties> springData;


	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getSchemaLocation() {
		return schemaLocation;
	}

	public void setSchemaLocation(String schemaLocation) {
		this.schemaLocation = schemaLocation;
	}

	public Map<String, RepositoryProperties> getSpringData() {
		return springData;
	}

	public void setSpringData(Map<String, RepositoryProperties> artifactRepositories) {
		this.springData = artifactRepositories;
	}
}
