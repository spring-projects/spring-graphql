/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
class CachingDocumentSourceTests {

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
