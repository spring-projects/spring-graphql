package org.springframework.graphql.execution.preparsed;

import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;

import java.util.function.Function;

/**
 * A {@link PreparsedDocumentProvider} calling the {@code parseAndValidateFunction}, and doing nothing else.
 */
public final class SpringNoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {

	public static final SpringNoOpPreparsedDocumentProvider INSTANCE = new SpringNoOpPreparsedDocumentProvider();

	private SpringNoOpPreparsedDocumentProvider() {
	}

	@Override
	public PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
			Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
		return parseAndValidateFunction.apply(executionInput);
	}

}
