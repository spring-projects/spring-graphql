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

package org.springframework.graphql.docs.client.httpsyncgraphqlclient;

import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;

public class SyncClientUsage {

	void createClient() {
		// tag::create[]
		RestClient restClient = RestClient.create("https://spring.io/graphql");
		HttpSyncGraphQlClient graphQlClient = HttpSyncGraphQlClient.create(restClient);
		// end::create[]
	}

	void mutateClient() {
		// tag::mutate[]
		RestClient restClient = RestClient.create("https://spring.io/graphql");
		HttpSyncGraphQlClient graphQlClient = HttpSyncGraphQlClient.builder(restClient)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();

		// Perform requests with graphQlClient...

		HttpSyncGraphQlClient anotherGraphQlClient = graphQlClient.mutate()
				.headers((headers) -> headers.setBasicAuth("peter", "..."))
				.build();

		// Perform requests with anotherGraphQlClient...

		// end::mutate[]
	}
}
