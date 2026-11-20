/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.argumentvalue;

import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.graphql.client.json.GraphQlJacksonModule;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

public class ArgumentValueClient {

	private final HttpGraphQlClient graphQlClient;

	// tag::createclient[]
	public ArgumentValueClient(HttpGraphQlClient graphQlClient) {
		JsonMapper jsonMapper = JsonMapper.builder().addModule(new GraphQlJacksonModule()).build();
		JacksonJsonEncoder jsonEncoder = new JacksonJsonEncoder(jsonMapper);
		WebClient webClient = WebClient.builder()
				.baseUrl("https://example.com/graphql")
				.codecs((codecs) -> codecs.defaultCodecs().jacksonJsonEncoder(jsonEncoder))
				.build();
		this.graphQlClient = HttpGraphQlClient.create(webClient);
	}
	// end::createclient[]

	// tag::argumentvalue[]
	public void updateProject() {
		ProjectInput projectInput = new ProjectInput("spring-graphql",
				ArgumentValue.ofNullable("Spring for GraphQL")); // <1>
		ClientGraphQlResponse response = this.graphQlClient.document("""
						mutation updateProject($project: ProjectInput!) {
							  updateProject($project: $project) {
								id
								name
							  }
						}
						""")
				.variables(Map.of("project", projectInput))
				.executeSync();
	}
	// end::argumentvalue[]
}
