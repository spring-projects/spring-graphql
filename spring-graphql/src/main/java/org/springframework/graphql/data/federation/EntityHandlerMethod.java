/*
 * Copyright 2002-2024 the original author or authors.
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


	Mono<Object> getEntity(DataFetchingEnvironment env, Map<String, Object> representation) {
		env = EntityArgumentMethodArgumentResolver.wrap(env, representation);
		return doInvoke(env);
	}

	Mono<Object> getEntities(DataFetchingEnvironment env, List<Map<String, Object>> representations) {
		env = EntityArgumentMethodArgumentResolver.wrap(env, representations);
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
