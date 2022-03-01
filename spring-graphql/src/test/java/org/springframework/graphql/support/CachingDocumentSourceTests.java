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

package org.springframework.graphql.support;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResourceDocumentSource}.
 * @author Rossen Stoyanchev
 */
public class CachingDocumentSourceTests {

	private CachingDocumentSource source;


	@BeforeEach
	void setUp() {
		DocumentSource resourceSource = new ResourceDocumentSource(
				Collections.singletonList(new ClassPathResource("books/")),
				ResourceDocumentSource.FILE_EXTENSIONS);

		this.source = new CachingDocumentSource(resourceSource);
	}


	@Test
	void cachingDefault() {
		assertThat(this.source.isCacheEnabled()).isTrue();
	}

	@Test
	void cachingOn() {
		this.source.setCacheEnabled(true);

		Mono<String> documentMono1 = source.getDocument("book-document");
		Mono<String> documentMono2 = source.getDocument("book-document");

		assertThat(documentMono1).isSameAs(documentMono2);

		String document1 = documentMono1.block();
		String document2 = documentMono2.block();

		assertThat(document1).isSameAs(document2);
	}

	@Test
	void cachingOff() {
		this.source.setCacheEnabled(false);

		Mono<String> documentMono1 = source.getDocument("book-document");
		Mono<String> documentMono2 = source.getDocument("book-document");

		assertThat(documentMono1).isNotSameAs(documentMono2);

		String document1 = documentMono1.block();
		String document2 = documentMono2.block();

		assertThat(document1).isNotSameAs(document2);
	}

}
