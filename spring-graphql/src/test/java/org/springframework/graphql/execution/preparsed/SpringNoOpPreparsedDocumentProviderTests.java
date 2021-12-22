package org.springframework.graphql.execution.preparsed;

import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.language.Document;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringNoOpPreparsedDocumentProvider}.
 */
public class SpringNoOpPreparsedDocumentProviderTests {

	@Test
	public void noOpDocumentProviderAppliesFunction() {
		SpringNoOpPreparsedDocumentProvider documentProvider = SpringNoOpPreparsedDocumentProvider.INSTANCE;
		PreparsedDocumentEntry documentEntry = new PreparsedDocumentEntry(Document.newDocument().build());
		PreparsedDocumentEntry providerResult = documentProvider
				.getDocument(ExecutionInput.newExecutionInput("{}").build(), executionInput -> documentEntry);

		assertThat(documentEntry).isEqualTo(providerResult);
	}

	@Test
	public void springNoOpDocumentProviderInstanceIsNotNull() {
		assertThat(SpringNoOpPreparsedDocumentProvider.INSTANCE).isNotNull();
	}

}
