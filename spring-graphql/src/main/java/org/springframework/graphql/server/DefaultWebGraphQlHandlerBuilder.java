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

package org.springframework.graphql.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.micrometer.context.ContextSnapshot;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.server.WebGraphQlInterceptor.Chain;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;


/**
 * Default implementation of {@link WebGraphQlHandler.Builder}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultWebGraphQlHandlerBuilder implements WebGraphQlHandler.Builder {

	private final ExecutionGraphQlService service;

	private final List<WebGraphQlInterceptor> interceptors = new ArrayList<>();

	@Nullable
	private WebSocketGraphQlInterceptor webSocketInterceptor;


	DefaultWebGraphQlHandlerBuilder(ExecutionGraphQlService service) {
		Assert.notNull(service, "GraphQlService is required");
		this.service = service;
	}


	@Override
	public WebGraphQlHandler.Builder interceptor(WebGraphQlInterceptor... interceptors) {
		return interceptors(Arrays.asList(interceptors));
	}

	@Override
	public WebGraphQlHandler.Builder interceptors(List<WebGraphQlInterceptor> interceptors) {
		this.interceptors.addAll(interceptors);
		interceptors.forEach(interceptor -> {
			if (interceptor instanceof WebSocketGraphQlInterceptor) {
				Assert.isNull(this.webSocketInterceptor, "There can be at most 1 WebSocketInterceptor");
				this.webSocketInterceptor = (WebSocketGraphQlInterceptor) interceptor;
			}
		});
		return this;
	}

	@Override
	public WebGraphQlHandler build() {

		Chain endOfChain = request -> this.service.execute(request).map(WebGraphQlResponse::new);

		Chain executionChain = this.interceptors.stream()
				.reduce(WebGraphQlInterceptor::andThen)
				.map(interceptor -> interceptor.apply(endOfChain))
				.orElse(endOfChain);

		return new WebGraphQlHandler() {

			@Override
			public WebSocketGraphQlInterceptor getWebSocketInterceptor() {
				return (webSocketInterceptor != null ?
						webSocketInterceptor : new WebSocketGraphQlInterceptor() {});
			}

			@Override
			public Mono<WebGraphQlResponse> handleRequest(WebGraphQlRequest request) {
				ContextSnapshot snapshot = ContextSnapshot.captureAll();
				return executionChain.next(request).contextWrite(snapshot::updateContext);
			}
		};
	}

}
