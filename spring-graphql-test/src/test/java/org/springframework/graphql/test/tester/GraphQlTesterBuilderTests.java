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

package org.springframework.graphql.test.tester;

import graphql.GraphqlErrorBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DocumentSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlTester} builder with a mock {@link ExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlTesterBuilderTests extends GraphQlTesterTestSupport {

	private static final String DOCUMENT = "{ Query }";


	@Test
	void mutateDocumentSource() {

		DocumentSource documentSource = name -> name.equals("name") ?
				Mono.just(DOCUMENT) : Mono.error(new IllegalArgumentException());

		setMockResponse("{}");

		// Original
		GraphQlTester.Builder<?> builder = graphQlTesterBuilder().documentSource(documentSource);
		GraphQlTester tester = builder.build();
		tester.documentName("name").execute();

		assertThat(request().getDocument()).isEqualTo(DOCUMENT);

		// Mutate
		tester = tester.mutate().build();
		tester.documentName("name").execute();

		assertThat(request().getDocument()).isEqualTo(DOCUMENT);
	}

	@Test
	void errorsFilteredGlobally() {

		String document = "{me {name, friends}}";

		setMockResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		graphQlTesterBuilder()
				.errorFilter((error) -> error.getMessage().startsWith("some "))
				.build()
				.document(document)
				.execute()
				.errors().verify()
				.path("me").pathDoesNotExist();

		assertThat(request().getDocument()).contains(document);
	}

}
