/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
