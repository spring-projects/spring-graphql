/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
