/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.support;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResourceDocumentSource}.
 * @author Rossen Stoyanchev
 */
class ResourceDocumentSourceTests {

	private ResourceDocumentSource source;


	@BeforeEach
	void setUp() {
		this.source = new ResourceDocumentSource(
				Collections.singletonList(new ClassPathResource("books/")),
				ResourceDocumentSource.FILE_EXTENSIONS);
	}


	@Test
	void getDocument() {
		String content = this.source.getDocument("book-document").block(Duration.ofSeconds(5));
		assertThat(content).startsWith("bookById(id:\"1\"");
	}

	@Test
	void getDocumentNotFound() {
		StepVerifier.create(this.source.getDocument("invalid"))
				.expectErrorMessage(
						"Failed to find document, name='invalid', " +
								"under location(s)=[class path resource [books/]]")
				.verify(Duration.ofSeconds(5));
	}

}
