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
