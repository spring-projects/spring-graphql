/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.requests.documentsource;

import org.springframework.graphql.client.GraphQlClient;

public class DocumentSource {

	void documentSource() {
		GraphQlClient graphQlClient = null;
		// tag::documentSource[]
		Project project = graphQlClient.documentName("projectReleases") // <1>
				.variable("slug", "spring-framework") // <2>
				.retrieveSync("projectReleases.project")
				.toEntity(Project.class);
		// end::documentSource[]
	}

	record Project() {

	}
}
