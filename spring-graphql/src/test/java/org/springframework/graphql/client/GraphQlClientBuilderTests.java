/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.graphql.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.support.DocumentSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link GraphQlClient} builder.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlClientBuilderTests extends GraphQlClientTestSupport {

	private static final String DOCUMENT = "{ Query }";


	@Test
	void defaultDocumentSource() {
		String document = "{ greeting }";
		getGraphQlService().setDataAsJson(document, "{}");
		graphQlClient().documentName("greeting").execute().block();
		assertThat(getGraphQlService().getGraphQlRequest().getDocument()).isEqualTo(document);
	}

	@Test
	void mutateDocumentSource() {

		DocumentSource documentSource = name -> name.equals("name") ?
				Mono.just(DOCUMENT) : Mono.error(new IllegalArgumentException());

		getGraphQlService().setDataAsJson(DOCUMENT, "{}");

		// Original
		GraphQlClient.Builder<?> builder = graphQlClientBuilder().documentSource(documentSource);
		GraphQlClient client = builder.build();
		ClientGraphQlResponse response = client.documentName("name").execute().block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.isValid()).isTrue();

		// Mutate
		client = client.mutate().build();
		response = client.documentName("name").execute().block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.isValid()).isTrue();
	}

	@Test
	void mutateInterceptors() {

		String name = "name1";
		String value = "value1";

		Map<String, Object> savedAttributes = new HashMap<>();

		GraphQlClientInterceptor savingInterceptor =
				initInterceptor(request -> savedAttributes.putAll(request.getAttributes()));

		GraphQlClientInterceptor changingInterceptor =
				initInterceptor(request -> request.getAttributes().computeIfPresent(name, (k, v) -> v + "2"));

		getGraphQlService().setDataAsJson(DOCUMENT, "{}");

		// Original
		GraphQlClient.Builder<?> builder = graphQlClientBuilder().interceptor(savingInterceptor);
		GraphQlClient client = builder.build();
		GraphQlResponse response = client.document(DOCUMENT).attribute(name, value).execute().block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.isValid()).isTrue();
		assertThat(savedAttributes).hasSize(1).containsEntry(name, value);

		// Mutate
		savedAttributes.clear();
		client = client.mutate().interceptors(interceptors -> interceptors.add(0, changingInterceptor)).build();
		response = client.document(DOCUMENT).attribute(name, value).execute().block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.isValid()).isTrue();
		assertThat(savedAttributes).hasSize(1).containsEntry(name, value + "2");
	}

	private static GraphQlClientInterceptor initInterceptor(Consumer<ClientGraphQlRequest> requestConsumer) {
		return new GraphQlClientInterceptor() {
			@Override
			public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
				requestConsumer.accept(request);
				return chain.next(request);
			}
		};
	}

}
