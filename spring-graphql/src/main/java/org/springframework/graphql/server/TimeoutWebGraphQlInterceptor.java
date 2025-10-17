/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.server;

import java.time.Duration;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link WebGraphQlInterceptor Web interceptor} that enforces a request timeout
 * for GraphQL requests. By default, timeouts will result in
 * {@link HttpStatus#REQUEST_TIMEOUT} responses.
 * <p>For streaming responses (like subscriptions), this timeout is only enforced
 * until the response stream is established. Transport-specific timeouts are
 * configurable on the transport handlers directly.
 * @author Brian Clozel
 * @since 1.4.0
 */
public class TimeoutWebGraphQlInterceptor implements WebGraphQlInterceptor {

	private final Duration timeout;

	private final HttpStatus timeoutStatus;

	/**
	 * Create a new interceptor for the given timeout duration.
	 * @param timeout the request timeout to enforce
	 */
	public TimeoutWebGraphQlInterceptor(Duration timeout) {
		this(timeout, HttpStatus.REQUEST_TIMEOUT);
	}

	/**
	 * Create a new interceptor for the given timeout duration and response status.
	 * @param timeout the request timeout to enforce
	 * @param timeoutStatus the HTTP response status to use in case of timeouts
	 */
	public TimeoutWebGraphQlInterceptor(Duration timeout, HttpStatus timeoutStatus) {
		this.timeout = timeout;
		this.timeoutStatus = timeoutStatus;
	}

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		return chain.next(request)
				.timeout(this.timeout, Mono.error(new ResponseStatusException(this.timeoutStatus)));
	}

}
