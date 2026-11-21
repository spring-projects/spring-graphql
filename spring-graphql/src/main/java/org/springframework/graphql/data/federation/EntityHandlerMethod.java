/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.federation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.support.DataFetcherHandlerMethodSupport;
import org.springframework.graphql.execution.ReactiveAdapterRegistryHelper;

/**
 * Invokable controller method to fetch a federated entity.
 *
 * @author Rossen Stoyanchev
 */
final class EntityHandlerMethod extends DataFetcherHandlerMethodSupport {

	private final boolean batchHandlerMethod;


	EntityHandlerMethod(
			FederationSchemaFactory.EntityMappingInfo info, HandlerMethodArgumentResolverComposite resolvers,
			@Nullable Executor executor, boolean invokeAsync) {

		super(info.handlerMethod(), resolvers, executor, invokeAsync);
		this.batchHandlerMethod = info.isBatchHandlerMethod();
	}


	boolean isBatchHandlerMethod() {
		return this.batchHandlerMethod;
	}


	Mono<Object> getEntity(DataFetchingEnvironment env, Map<String, Object> representation, EntityKeyResolver resolver) {
		env = EntityArgumentMethodArgumentResolver.wrap(env, representation, resolver);
		return doInvoke(env);
	}

	Mono<Object> getEntities(DataFetchingEnvironment env, List<Map<String, Object>> representations, EntityKeyResolver resolver) {
		env = EntityArgumentMethodArgumentResolver.wrap(env, representations, resolver);
		return doInvoke(env);
	}

	private Mono<Object> doInvoke(DataFetchingEnvironment env) {
		@Nullable Object[] args;
		try {
			args = getMethodArgumentValues(env);
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}
		Object result = doInvoke(env.getGraphQlContext(), args);
		return ReactiveAdapterRegistryHelper.toMono(result);
	}

}
