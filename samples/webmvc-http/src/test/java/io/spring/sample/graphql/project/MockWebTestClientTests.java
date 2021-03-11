/*
 * Copyright 2002-2021 the original author or authors.
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
package io.spring.sample.graphql.project;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example tests using {@link WebTestClient} to connect to {@link MockMvc} as
 * the "mock" server, i.e. without running an HTTP server.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class MockWebTestClientTests {

	private WebTestClient client;

	@BeforeEach
	public void setUp(@Autowired MockMvc mockMvc) {
		this.client = MockMvcWebTestClient.bindTo(mockMvc)
				.baseUrl("/graphql")
				.defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
				.build();
	}

	@Test
	void jsonPath() {
		String query = "{" +
				"  project(slug:\"spring-framework\") {" +
				"    releases {" +
				"      version" +
				"    }" +
				"  }" +
				"}";

		this.client.post().bodyValue(JsonRequest.create(query))
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.data.project.releases[*].version")
				.value((Consumer<List<String>>) versions -> {
					assertThat(versions).hasSizeGreaterThan(1);
				});
	}

	@Test
	void jsonContent() {
		String query = "{" +
				"  project(slug:\"spring-framework\") {" +
				"    repositoryUrl" +
				"  }" +
				"}";

		String expectedJson = "{" +
				"  \"data\":{" +
				"    \"project\":{" +
				"      \"repositoryUrl\":\"http://github.com/spring-projects/spring-framework\"" +
				"    }" +
				"  }" +
				"}";

		this.client.post().bodyValue(JsonRequest.create(query))
				.exchange()
				.expectStatus().isOk()
				.expectBody().json(expectedJson);
	}

	@Test
	void decodedResponse() {
		String query = "{" +
				"  project(slug:\"spring-framework\") {" +
				"    releases {" +
				"      version" +
				"    }" +
				"  }" +
				"}";

		this.client.post().bodyValue(JsonRequest.create(query))
				.exchange()
				.expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<JsonResponse<Project>>() {})
				.consumeWith(exchangeResult -> {
					Project project = exchangeResult.getResponseBody().getDataEntry();
					assertThat(project.getReleases()).hasSizeGreaterThan(1);
				});
	}

}
