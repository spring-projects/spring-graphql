/*
 * Copyright 2002-2023 the original author or authors.
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
