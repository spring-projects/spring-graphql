/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.support;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResourceDocumentSource}.
 * @author Rossen Stoyanchev
 * @author Marco Schäck
 */
class ResourceDocumentSourceTests {

	@Nested
	class DefaultConstructorTests {

		@Test
		void getDocument() {
			ResourceDocumentSource source = new ResourceDocumentSource();
			String content = source.getDocument("test-document").block(Duration.ofSeconds(5));
			assertThat(content).startsWith("query GetUser($id: ID!)");
		}

		@Test
		void getDocumentNotFound() {
			ResourceDocumentSource source = new ResourceDocumentSource();
			StepVerifier.create(source.getDocument("invalid"))
					.expectErrorMessage(
							"Failed to find document, name='invalid', " +
									"under location(s)=[class path resource [graphql/]]")
					.verify(Duration.ofSeconds(5));
		}
	}

	@Nested
	class SingleResourceConstructorTests {

		@Test
		void getDocument() {
			ResourceDocumentSource source = new ResourceDocumentSource(new ClassPathResource("books/"));
			String content = source.getDocument("book-document").block(Duration.ofSeconds(5));
			assertThat(content).startsWith("bookById(id:\"1\"");
		}

		@Test
		void getDocumentNotFound() {
			ResourceDocumentSource source = new ResourceDocumentSource(new ClassPathResource("books/"));
			StepVerifier.create(source.getDocument("invalid"))
					.expectErrorMessage(
							"Failed to find document, name='invalid', " +
									"under location(s)=[class path resource [books/]]")
					.verify(Duration.ofSeconds(5));
		}
	}

	@Nested
	class ListResourceConstructorTests {

		@Test
		void getDocument() {
			ResourceDocumentSource source = new ResourceDocumentSource(
					Collections.singletonList(new ClassPathResource("books/")),
					ResourceDocumentSource.FILE_EXTENSIONS);
			String content = source.getDocument("book-document").block(Duration.ofSeconds(5));
			assertThat(content).startsWith("bookById(id:\"1\"");
		}

		@Test
		void getDocumentNotFound() {
			ResourceDocumentSource source = new ResourceDocumentSource(
					Collections.singletonList(new ClassPathResource("books/")),
					ResourceDocumentSource.FILE_EXTENSIONS);
			StepVerifier.create(source.getDocument("invalid"))
					.expectErrorMessage(
							"Failed to find document, name='invalid', " +
									"under location(s)=[class path resource [books/]]")
					.verify(Duration.ofSeconds(5));
		}
	}

}
