/*
 * Copyright 2002-2021 the original author or authors.
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
import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlService;
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

	private final GraphQlService service;

	@Nullable
	private List<WebInterceptor> interceptors;

	@Nullable
	private List<ThreadLocalAccessor> accessors;

	DefaultWebGraphQlHandlerBuilder(GraphQlService service) {
		Assert.notNull(service, "GraphQlService is required");
		this.service = service;
	}

	@Override
	public WebGraphQlHandler.Builder interceptor(WebInterceptor... interceptors) {
		return interceptors(Arrays.asList(interceptors));
	}

	@Override
	public WebGraphQlHandler.Builder interceptors(List<WebInterceptor> interceptors) {
		if (!CollectionUtils.isEmpty(interceptors)) {
			this.interceptors = (this.interceptors != null) ? this.interceptors : new ArrayList<>();
			this.interceptors.addAll(interceptors);
		}
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
		List<WebInterceptor> interceptorsToUse =
				(this.interceptors != null) ? this.interceptors : Collections.emptyList();

		WebGraphQlHandler targetHandler = (webInput) ->
				this.service.execute(webInput).map((result) -> new WebOutput(webInput, result));

		WebGraphQlHandler interceptionChain = interceptorsToUse.stream()
				.reduce(WebInterceptor::andThen)
				.map((interceptor) -> (WebGraphQlHandler) (input) -> interceptor.intercept(input, targetHandler))
				.orElse(targetHandler);

		return (CollectionUtils.isEmpty(this.accessors) ? interceptionChain
				: new ThreadLocalExtractingHandler(interceptionChain, ThreadLocalAccessor.composite(this.accessors)));
	}

	/**
	 * {@link WebGraphQlHandler} that extracts ThreadLocal values and saves them in the
	 * Reactor context for subsequent use for DataFetcher's.
	 */
	private static class ThreadLocalExtractingHandler implements WebGraphQlHandler {

		private final WebGraphQlHandler delegate;

		private final ThreadLocalAccessor accessor;

		ThreadLocalExtractingHandler(WebGraphQlHandler delegate, ThreadLocalAccessor accessor) {
			this.delegate = delegate;
			this.accessor = accessor;
		}

		@Override
		public Mono<WebOutput> handle(WebInput input) {
			return this.delegate.handle(input).contextWrite((context) ->
					ReactorContextManager.extractThreadLocalValues(this.accessor, context));
		}

	}

}
