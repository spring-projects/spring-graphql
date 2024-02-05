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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.apollographql.federation.graphqljava._Entity;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionStepInfo;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DelegatingDataFetchingEnvironment;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.support.HandlerDataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.Nullable;

/**
 * DataFetcher that handles the "_entities" query by invoking
 * {@link EntityHandlerMethod}s.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 * @see com.apollographql.federation.graphqljava.SchemaTransformer#fetchEntities(DataFetcher)
 */
final class EntitiesDataFetcher implements DataFetcher<Mono<DataFetcherResult<List<Object>>>> {

	private final Map<String, EntityHandlerMethod> handlerMethods;

	private final HandlerDataFetcherExceptionResolver exceptionResolver;


	public EntitiesDataFetcher(
			Map<String, EntityHandlerMethod> handlerMethods, HandlerDataFetcherExceptionResolver resolver) {

		this.handlerMethods = new LinkedHashMap<>(handlerMethods);
		this.exceptionResolver = resolver;
	}


	@Override
	public Mono<DataFetcherResult<List<Object>>> get(DataFetchingEnvironment environment) {
		List<Map<String, Object>> representations = environment.getArgument(_Entity.argumentName);

		List<Mono<Object>> monoList = new ArrayList<>();
		for (int index = 0; index < representations.size(); index++) {
			Map<String, Object> map = representations.get(index);
			if (!(map.get("__typename") instanceof String typename)) {
				Exception ex = new RepresentationException(map, "Missing \"__typename\" argument");
				monoList.add(resolveException(ex, environment, null, index));
				continue;
			}
			EntityHandlerMethod handlerMethod = this.handlerMethods.get(typename);
			if (handlerMethod == null) {
				Exception ex = new RepresentationException(map, "No entity fetcher");
				monoList.add(resolveException(ex, environment, null, index));
				continue;
			}
			monoList.add(invokeResolver(environment, handlerMethod, map, index));
		}
		return Mono.zip(monoList, Arrays::asList).map(EntitiesDataFetcher::toDataFetcherResult);
	}

	private Mono<Object> invokeResolver(
			DataFetchingEnvironment env, EntityHandlerMethod handlerMethod, Map<String, Object> map, int index) {

		return handlerMethod.getEntity(env, map, index)
				.switchIfEmpty(Mono.error(new RepresentationNotResolvedException(map, handlerMethod)))
				.onErrorResume(ex -> resolveException(ex, env, handlerMethod, index));
	}

	private Mono<Object> resolveException(
			Throwable ex, DataFetchingEnvironment env, @Nullable EntityHandlerMethod handlerMethod, int index) {

		Throwable theEx = (ex instanceof CompletionException ? ex.getCause() : ex);
		DataFetchingEnvironment theEnv = new EntityDataFetchingEnvironment(env, index);
		Object handler = (handlerMethod != null ? handlerMethod.getBean() : null);

		return this.exceptionResolver.resolveException(theEx, theEnv, handler)
				.map(ErrorContainer::new)
				.switchIfEmpty(Mono.fromCallable(() -> createDefaultError(theEx, theEnv)))
				.cast(Object.class);
	}

	private ErrorContainer createDefaultError(Throwable ex, DataFetchingEnvironment env) {

		ErrorType errorType = (ex instanceof RepresentationException representationEx ?
				representationEx.getErrorType() : ErrorType.INTERNAL_ERROR);

		return new ErrorContainer(GraphqlErrorBuilder.newError(env)
				.errorType(errorType)
				.message(ex.getMessage())
				.build());
	}

	private static DataFetcherResult<List<Object>> toDataFetcherResult(List<Object> entities) {
		List<GraphQLError> errors = new ArrayList<>();
		for (int i = 0; i < entities.size(); i++) {
			Object entity = entities.get(i);
			if (entity instanceof ErrorContainer errorContainer) {
				errors.addAll(errorContainer.errors());
				entities.set(i, null);
			}
		}
		return DataFetcherResult.<List<Object>>newResult().data(entities).errors(errors).build();
	}


	private static class EntityDataFetchingEnvironment extends DelegatingDataFetchingEnvironment {

		private final ExecutionStepInfo executionStepInfo;

		public EntityDataFetchingEnvironment(DataFetchingEnvironment env, int index) {
			super(env);
			this.executionStepInfo = ExecutionStepInfo.newExecutionStepInfo(env.getExecutionStepInfo())
					.path(env.getExecutionStepInfo().getPath().segment(index))
					.build();
		}

		@Override
		public ExecutionStepInfo getExecutionStepInfo() {
			return this.executionStepInfo;
		}
	}


	private record ErrorContainer(List<GraphQLError> errors) {

		ErrorContainer(GraphQLError error) {
			this(Collections.singletonList(error));
		}
	}

}
