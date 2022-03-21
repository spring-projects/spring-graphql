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

package org.springframework.graphql.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.execution.ReactorContextManager;
import org.springframework.graphql.execution.ThreadLocalAccessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;


/**
 * Default implementation of {@link WebGraphQlHandler.Builder}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultWebGraphQlHandlerBuilder implements WebGraphQlHandler.Builder {

	private final ExecutionGraphQlService service;

	private final List<WebInterceptor> interceptors = new ArrayList<>();

	@Nullable
	private WebSocketInterceptor webSocketInterceptor;

	@Nullable
	private List<ThreadLocalAccessor> accessors;


	DefaultWebGraphQlHandlerBuilder(ExecutionGraphQlService service) {
		Assert.notNull(service, "GraphQlService is required");
		this.service = service;
	}


	@Override
	public WebGraphQlHandler.Builder interceptor(WebInterceptor... interceptors) {
		return interceptors(Arrays.asList(interceptors));
	}

	@Override
	public WebGraphQlHandler.Builder interceptors(List<WebInterceptor> interceptors) {
		this.interceptors.addAll(interceptors);
		interceptors.forEach(interceptor -> {
			if (interceptor instanceof WebSocketInterceptor) {
				Assert.isNull(this.webSocketInterceptor, "There can be at most 1 WebSocketInterceptor");
				this.webSocketInterceptor = (WebSocketInterceptor) interceptor;
			}
		});
		return this;
	}

	@Override
	public WebGraphQlHandler.Builder threadLocalAccessor(ThreadLocalAccessor... accessors) {
		return threadLocalAccessors(Arrays.asList(accessors));
	}

	@Override
	public WebGraphQlHandler.Builder threadLocalAccessors(List<ThreadLocalAccessor> accessors) {
		if (!CollectionUtils.isEmpty(accessors)) {
			this.accessors = (this.accessors != null) ? this.accessors : new ArrayList<>();
			this.accessors.addAll(accessors);
		}
		return this;
	}

	@Override
	public WebGraphQlHandler build() {

		WebInterceptor.Chain endOfChain =
				request -> this.service.execute(request).map(WebGraphQlResponse::new);

		WebInterceptor.Chain chain = this.interceptors.stream()
				.reduce(WebInterceptor::andThen)
				.map(interceptor -> (WebInterceptor.Chain) (request) -> interceptor.intercept(request, endOfChain))
				.orElse(endOfChain);

		return new WebGraphQlHandler() {

			@Override
			public Mono<WebGraphQlResponse> handleRequest(WebGraphQlRequest request) {
				return chain.next(request)
						.contextWrite(context -> {
							if (!CollectionUtils.isEmpty(accessors)) {
								ThreadLocalAccessor accessor = ThreadLocalAccessor.composite(accessors);
								return ReactorContextManager.extractThreadLocalValues(accessor, context);
							}
							return context;
						});
			}

			@Override
			public WebSocketInterceptor webSocketInterceptor() {
				return (webSocketInterceptor != null ? webSocketInterceptor : new WebSocketInterceptor() {});
			}

		};
	}

}
