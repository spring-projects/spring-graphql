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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.util.Assert;

/**
 * DataFetcher that handles the "_entities" query by invoking
 * {@link EntityHandlerMethod}s.
 *
 * @author Rossen Stoyanchev
 * @see com.apollographql.federation.graphqljava.SchemaTransformer#fetchEntities(DataFetcher)
 */
final class EntitiesDataFetcher implements DataFetcher<Mono<DataFetcherResult<List<Object>>>> {

	private final Map<String, EntityHandlerMethod> handlerMethods;

	private final HandlerDataFetcherExceptionResolver exceptionResolver;


	EntitiesDataFetcher(
			Map<String, EntityHandlerMethod> handlerMethods, HandlerDataFetcherExceptionResolver resolver) {

		this.handlerMethods = new LinkedHashMap<>(handlerMethods);
		this.exceptionResolver = resolver;
	}


	@Override
	public Mono<DataFetcherResult<List<Object>>> get(DataFetchingEnvironment env) {
		List<Map<String, Object>> representations = env.getArgument(_Entity.argumentName);
		if (representations == null) {
			return Mono.error(new RepresentationException(
					Collections.emptyMap(), "Missing \"representations\" argument"));
		}

		Set<String> batchedTypes = new HashSet<>();
		List<Mono<?>> monoList = new ArrayList<>();
		for (int index = 0; index < representations.size(); index++) {
			Map<String, Object> map = representations.get(index);
			if (!(map.get("__typename") instanceof String type)) {
				Exception ex = new RepresentationException(map, "Missing \"__typename\" argument");
				monoList.add(resolveException(ex, env, null, index));
				continue;
			}
			EntityHandlerMethod handlerMethod = this.handlerMethods.get(type);
			if (handlerMethod == null) {
				Exception ex = new RepresentationException(map, "No entity fetcher");
				monoList.add(resolveException(ex, env, null, index));
				continue;
			}

			if (!handlerMethod.isBatchHandlerMethod()) {
				monoList.add(invokeEntityMethod(env, handlerMethod, map, index));
			}
			else {
				if (!batchedTypes.contains(type)) {
					EntityBatchDelegate delegate = new EntityBatchDelegate(env, representations, handlerMethod, type);
					monoList.add(delegate.invokeEntityBatchMethod());
					batchedTypes.add(type);
				}
				else {
					// Covered by batch invocation, but zip needs a value (to be replaced by batch results)
					monoList.add(Mono.just(Collections.emptyMap()));
				}
			}
		}
		return Mono.zip(monoList, Arrays::asList).map(EntitiesDataFetcher::toDataFetcherResult);
	}

	private Mono<Object> invokeEntityMethod(
			DataFetchingEnvironment env, EntityHandlerMethod handlerMethod, Map<String, Object> map, int index) {

		return handlerMethod.getEntity(env, map)
				.switchIfEmpty(Mono.error(new RepresentationNotResolvedException(map, handlerMethod)))
				.onErrorResume((ex) -> resolveException(ex, env, handlerMethod, index));
	}

	private Mono<ErrorContainer> resolveException(
			Throwable ex, DataFetchingEnvironment env, @Nullable EntityHandlerMethod handlerMethod, int index) {

		Throwable theEx = (ex instanceof CompletionException) ? ex.getCause() : ex;
		DataFetchingEnvironment theEnv = new IndexedDataFetchingEnvironment(env, index);
		Object handler = (handlerMethod != null) ? handlerMethod.getBean() : null;

		return this.exceptionResolver.resolveException(theEx, theEnv, handler)
				.map(ErrorContainer::new)
				.switchIfEmpty(Mono.fromCallable(() -> createDefaultError(theEx, theEnv)));
	}

	private ErrorContainer createDefaultError(Throwable ex, DataFetchingEnvironment env) {

		ErrorType errorType = (ex instanceof RepresentationException representationEx) ?
				representationEx.getErrorType() : ErrorType.INTERNAL_ERROR;

		return new ErrorContainer(GraphqlErrorBuilder.newError(env)
				.errorType(errorType)
				.message(ex.getMessage())
				.build());
	}

	private static DataFetcherResult<List<Object>> toDataFetcherResult(List<Object> entities) {
		List<GraphQLError> errors = new ArrayList<>();
		for (int i = 0; i < entities.size(); i++) {
			Object entity = entities.get(i);
			if (entity instanceof EntityBatchDelegate delegate) {
				delegate.processResults(entities, errors);
			}
			if (entity instanceof ErrorContainer errorContainer) {
				errors.addAll(errorContainer.errors());
				entities.set(i, null);
			}
		}
		return DataFetcherResult.<List<Object>>newResult().data(entities).errors(errors).build();
	}


	private class EntityBatchDelegate {

		private final DataFetchingEnvironment environment;

		private final EntityHandlerMethod handlerMethod;

		private final List<Map<String, Object>> filteredRepresentations = new ArrayList<>();

		private final List<Integer> indexes = new ArrayList<>();

		@Nullable
		private List<?> resultList;

		EntityBatchDelegate(
				DataFetchingEnvironment env, List<Map<String, Object>> allRepresentations,
				EntityHandlerMethod handlerMethod, String type) {

			this.environment = env;
			this.handlerMethod = handlerMethod;
			for (int i = 0; i < allRepresentations.size(); i++) {
				Map<String, Object> map = allRepresentations.get(i);
				if (type.equals(map.get("__typename"))) {
					this.filteredRepresentations.add(map);
					this.indexes.add(i);
				}
			}
		}

		Mono<Object> invokeEntityBatchMethod() {
			return this.handlerMethod.getEntities(this.environment, this.filteredRepresentations)
					.mapNotNull((result) -> (((List<?>) result).isEmpty()) ? null : result)
					.switchIfEmpty(Mono.defer(this::handleEmptyResult))
					.onErrorResume(this::handleErrorResult)
					.map((result) -> {
						this.resultList = (List<?>) result;
						return this;
					});
		}

		Mono<Object> handleEmptyResult() {
			List<Mono<?>> exceptions = new ArrayList<>(this.indexes.size());
			for (int i = 0; i < this.indexes.size(); i++) {
				Map<String, Object> map = this.filteredRepresentations.get(i);
				Exception ex = new RepresentationNotResolvedException(map, this.handlerMethod);
				exceptions.add(resolveException(ex, this.environment, this.handlerMethod, this.indexes.get(i)));
			}
			return Mono.zip(exceptions, Arrays::asList);
		}

		Mono<List<Object>> handleErrorResult(Throwable ex) {
			List<Mono<?>> list = new ArrayList<>();
			for (Integer index : this.indexes) {
				list.add(resolveException(ex, this.environment, this.handlerMethod, index));
			}
			return Mono.zip(list, Arrays::asList);
		}

		void processResults(List<Object> entities, List<GraphQLError> errors) {
			Assert.state(this.resultList != null, "Expected resultList");
			for (int i = 0; i < this.resultList.size(); i++) {
				Object entity = this.resultList.get(i);
				if (entity instanceof ErrorContainer errorContainer) {
					errors.addAll(errorContainer.errors());
					entity = null;
				}
				entities.set(this.indexes.get(i), entity);
			}
		}
	}


	private static class IndexedDataFetchingEnvironment extends DelegatingDataFetchingEnvironment {

		private final ExecutionStepInfo executionStepInfo;

		IndexedDataFetchingEnvironment(DataFetchingEnvironment env, int index) {
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
