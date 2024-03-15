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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.support.DataFetcherHandlerMethodSupport;
import org.springframework.lang.Nullable;

/**
 * Invokable controller method to fetch a federated entity.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
final class EntityHandlerMethod extends DataFetcherHandlerMethodSupport {

	public EntityHandlerMethod(
			HandlerMethod handlerMethod, HandlerMethodArgumentResolverComposite resolvers,
			@Nullable Executor executor) {

		super(handlerMethod, resolvers, executor);
	}


	public Mono<Object> getEntity(
			DataFetchingEnvironment environment, Map<String, Object> representation, int index) {

		Object[] args;
		try {
			environment = EntityArgumentMethodArgumentResolver.wrap(environment, representation);
			args = getMethodArgumentValues(environment, representation);
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}

		Object result = doInvoke(environment.getGraphQlContext(), args);

		if (result instanceof Mono<?> mono) {
			return mono.cast(Object.class);
		}
		else if (result instanceof CompletableFuture<?> future) {
			return Mono.fromFuture(future);
		}
		else {
			return Mono.justOrEmpty(result);
		}
	}

}
