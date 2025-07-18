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

package org.springframework.graphql.docs.client.requests.retrieve;

import reactor.core.publisher.Mono;

import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.FieldAccessException;
import org.springframework.graphql.client.GraphQlClient;

public class Retrieve {

	void retrieveSync() {
		GraphQlClient graphQlClient = null;

		// tag::retrieveSync[]
		String document =
			"""
			{
				project(slug:"spring-framework") {
					name
					releases {
						version
					}
				}
			}
			""";

		Project project = graphQlClient.document(document) // <1>
			.retrieveSync("project") // <2>
			.toEntity(Project.class); // <3>
		// end::retrieveSync[]
	}

	void retrieve() {
		GraphQlClient graphQlClient = null;

		// tag::retrieve[]
		String document =
			"""
			{
				project(slug:"spring-framework") {
					name
					releases {
						version
					}
				}
			}
			""";

		Mono<Project> project = graphQlClient.document(document) // <1>
				.retrieve("project") // <2>
				.toEntity(Project.class); // <3>
		// end::retrieve[]
	}

	Project fieldErrorSync() {
		GraphQlClient graphQlClient = null;
		String document = "";

		// tag::fieldErrorSync[]
		try {
			Project project = graphQlClient.document(document)
					.retrieveSync("project")
					.toEntity(Project.class);
			return project;
		}
		catch (FieldAccessException ex) {
			ClientGraphQlResponse response = ex.getResponse();
			// ...
			ClientResponseField field = ex.getField();
			// return fallback value
			return new Project();
		}
		// end::fieldErrorSync[]
	}

	void fieldError() {
		GraphQlClient graphQlClient = null;
		String document = "";

		// tag::fieldError[]
		Mono<Project> projectMono = graphQlClient.document(document)
				.retrieve("project")
				.toEntity(Project.class)
				.onErrorResume(FieldAccessException.class, (ex) -> {
					ClientGraphQlResponse response = ex.getResponse();
					// ...
					ClientResponseField field = ex.getField();
					// return fallback value
					return Mono.just(new Project());
				});
		// end::fieldError[]
	}

	record Project() {

	}
}
