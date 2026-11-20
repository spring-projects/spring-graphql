/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;

/**
 * {@link ExecutionGraphQlRequest} for use in tests with a convenient single-arg
 * constructor and simple incrementing id generation.
 *
 * @author Rossen Stoyanchev
 */
public final class TestExecutionRequest extends DefaultExecutionGraphQlRequest {

	private static final AtomicLong idIndex = new AtomicLong();


	private TestExecutionRequest(String document, Map<String, Object> vars) {
		super(document, null, vars, null, String.valueOf(idIndex.incrementAndGet()), Locale.ENGLISH);
	}


	public static ExecutionGraphQlRequest forDocument(String document) {
		return new TestExecutionRequest(document, Collections.emptyMap());
	}

	public static ExecutionGraphQlRequest forDocumentAndVars(String document, Map<String, Object> vars) {
		return new TestExecutionRequest(document, vars);
	}

}
