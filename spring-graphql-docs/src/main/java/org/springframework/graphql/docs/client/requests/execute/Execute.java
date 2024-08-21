/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.docs.client.requests.execute;

import reactor.core.publisher.Mono;

import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.GraphQlClient;

public class Execute {

	void executeSync() {
		GraphQlClient graphQlClient = null;
		String document = "";
		// tag::executeSync[]
		ClientGraphQlResponse response = graphQlClient.document(document).executeSync();

		if (!response.isValid()) {
			// Request failure... <1>
		}

		ClientResponseField field = response.field("project");
		if (field.getValue() == null) {
			if (field.getErrors().isEmpty()) {
				// Optional field set to null... <2>
			}
			else {
				// Field failure... <3>
			}
		}

		Project project = field.toEntity(Project.class); // <4>
		// end::executeSync[]
	}

	void execute() {
		GraphQlClient graphQlClient = null;
		String document = "";
		// tag::execute[]
		Mono<Project> projectMono = graphQlClient.document(document)
				.execute()
				.map((response) -> {
					if (!response.isValid()) {
						// Request failure... <1>
					}

					ClientResponseField field = response.field("project");
					if (field.getValue() == null) {
						if (field.getErrors().isEmpty()) {
							// Optional field set to null... <2>
						}
						else {
							// Field failure... <3>
						}
					}

					return field.toEntity(Project.class); // <4>
				});
		// end::execute[]
	}

	record Project() {

	}
}
