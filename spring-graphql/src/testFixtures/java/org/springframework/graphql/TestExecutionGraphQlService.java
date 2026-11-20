/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import reactor.core.publisher.Mono;

/**
 * Wrap an {@link ExecutionGraphQlService} to expose an addition convenience
 * method that takes a String document, and essentially hides the call to
 * {@link TestExecutionRequest#forDocument(String)}.
 * @author Rossen Stoyanchev
 */
public class TestExecutionGraphQlService implements ExecutionGraphQlService {

	private final ExecutionGraphQlService delegate;


	public TestExecutionGraphQlService(ExecutionGraphQlService delegate) {
		this.delegate = delegate;
	}


	public ExecutionGraphQlService getDelegate() {
		return this.delegate;
	}

	public Mono<ExecutionGraphQlResponse> execute(String document) {
		ExecutionGraphQlRequest request = TestExecutionRequest.forDocument(document);
		return execute(request);
	}

	@Override
	public Mono<ExecutionGraphQlResponse> execute(ExecutionGraphQlRequest request) {
		return this.delegate.execute(request);
	}

}
