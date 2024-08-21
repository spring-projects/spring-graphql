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

package org.springframework.graphql.docs.client.httpgraphqlclient;

import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

public class ClientUsage {

	void create() {
		// tag::create[]
		WebClient webClient = WebClient.create("https://spring.io/graphql");
		HttpGraphQlClient graphQlClient = HttpGraphQlClient.create(webClient);
		// end::create[]
	}

	void mutateClient() {
		// tag::mutate[]
		WebClient webClient = WebClient.create("https://spring.io/graphql");

		HttpGraphQlClient graphQlClient = HttpGraphQlClient.builder(webClient)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();

		// Perform requests with graphQlClient...

		HttpGraphQlClient anotherGraphQlClient = graphQlClient.mutate()
				.headers((headers) -> headers.setBasicAuth("peter", "..."))
				.build();

		// Perform requests with anotherGraphQlClient...
		// end::mutate[]
	}
}
