/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.docs.client.fieldvalue;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.graphql.FieldValue;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.graphql.client.json.GraphQlModule;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.WebClient;

public class FieldValueClient {

	private final HttpGraphQlClient graphQlClient;

	// tag::createclient[]
	public FieldValueClient(HttpGraphQlClient graphQlClient) {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
				.modulesToInstall(new GraphQlModule())
				.build();
		Jackson2JsonEncoder jsonEncoder = new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON);
		WebClient webClient = WebClient.builder()
				.baseUrl("https://example.com/graphql")
				.codecs((codecs) -> codecs.defaultCodecs().jackson2JsonEncoder(jsonEncoder))
				.build();
		this.graphQlClient = HttpGraphQlClient.create(webClient);
	}
	// end::createclient[]

	// tag::fieldvalue[]
	public void updateProject() {
		ProjectInput projectInput = new ProjectInput("spring-graphql",
				FieldValue.ofNullable("Spring for GraphQL"));
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
	// end::fieldvalue[]
}
