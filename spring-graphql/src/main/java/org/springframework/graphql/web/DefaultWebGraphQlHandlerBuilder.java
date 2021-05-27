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
import java.util.Collections;
import java.util.List;

import graphql.ExecutionInput;

import org.springframework.graphql.GraphQlService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link WebGraphQlHandler.Builder}.
 */
class DefaultWebGraphQlHandlerBuilder implements WebGraphQlHandler.Builder {

	private final GraphQlService service;

	@Nullable
	private List<WebInterceptor> interceptors;


	DefaultWebGraphQlHandlerBuilder(GraphQlService service) {
		Assert.notNull(service, "GraphQlService must not be null");
		this.service = service;
	}


	@Override
	public WebGraphQlHandler.Builder interceptors(List<WebInterceptor> interceptors) {
		this.interceptors = (this.interceptors != null ? this.interceptors : new ArrayList<>());
		this.interceptors.addAll(interceptors);
		return this;
	}

	@Override
	public WebGraphQlHandler build() {
		List<WebInterceptor> interceptorsToUse =
				(this.interceptors != null ? this.interceptors : Collections.emptyList());

		return interceptorsToUse.stream()
				.reduce(WebInterceptor::andThen)
				.map(interceptor -> (WebGraphQlHandler) input -> interceptor.intercept(input, createHandler()))
				.orElse(createHandler());
	}

	private WebGraphQlHandler createHandler() {
		return webInput -> {
			ExecutionInput input = webInput.toExecutionInput();
			return this.service.execute(input).map(result -> new WebOutput(webInput, result));
		};
	}

}
