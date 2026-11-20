/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.execution.ContextPropagationHelper;
import org.springframework.graphql.server.WebGraphQlInterceptor.Chain;
import org.springframework.util.Assert;


/**
 * Default implementation of {@link WebGraphQlHandler.Builder}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultWebGraphQlHandlerBuilder implements WebGraphQlHandler.Builder {

	private final ExecutionGraphQlService service;

	private final List<WebGraphQlInterceptor> interceptors = new ArrayList<>();

	private @Nullable ContextSnapshotFactory snapshotFactory;

	private @Nullable WebSocketGraphQlInterceptor webSocketInterceptor;


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
		interceptors.forEach((interceptor) -> {
			if (interceptor instanceof WebSocketGraphQlInterceptor) {
				Assert.isNull(this.webSocketInterceptor, "There can be at most 1 WebSocketInterceptor");
				this.webSocketInterceptor = (WebSocketGraphQlInterceptor) interceptor;
			}
		});
		return this;
	}

	@Override
	public WebGraphQlHandler.Builder contextSnapshotFactory(ContextSnapshotFactory snapshotFactory) {
		this.snapshotFactory = snapshotFactory;
		return this;
	}

	@Override
	public WebGraphQlHandler build() {

		ContextSnapshotFactory snapshotFactory = ContextPropagationHelper.selectInstance(this.snapshotFactory);

		Chain endOfChain = (request) -> this.service.execute(request).map(WebGraphQlResponse::new);

		Chain executionChain = this.interceptors.stream()
				.reduce(WebGraphQlInterceptor::andThen)
				.map((interceptor) -> interceptor.apply(endOfChain))
				.orElse(endOfChain);

		return new WebGraphQlHandler() {

			@Override
			public WebSocketGraphQlInterceptor getWebSocketInterceptor() {
				return (DefaultWebGraphQlHandlerBuilder.this.webSocketInterceptor != null) ?
						DefaultWebGraphQlHandlerBuilder.this.webSocketInterceptor : new WebSocketGraphQlInterceptor() { };
			}

			@Override
			public ContextSnapshotFactory contextSnapshotFactory() {
				return snapshotFactory;
			}

			@Override
			public Mono<WebGraphQlResponse> handleRequest(WebGraphQlRequest request) {
				ContextSnapshot snapshot = snapshotFactory.captureAll();
				return executionChain.next(request).contextWrite((context) -> {
					context = ContextPropagationHelper.saveInstance(snapshotFactory, context);
					return snapshot.updateContext(context);
				});
			}
		};
	}

}
