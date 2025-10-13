/*
 * Copyright 2002-present the original author or authors.
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
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.support.HandlerDataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;

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
		if (representations.isEmpty()) {
			return Mono.just(DataFetcherResult.<List<Object>>newResult().data(Collections.emptyList()).build());
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
					monoList.add(invokeEntitiesMethod(env, handlerMethod, representations, type));
					batchedTypes.add(type);
				}
				else {
					// Already covered, but zip needs a value (to be replaced by batch results)
					monoList.add(Mono.just(Collections.emptyMap()));
				}
			}
		}
		return Mono.zip(monoList, Arrays::asList).map(EntitiesDataFetcher::toDataFetcherResult);
	}

	private Mono<Object> invokeEntityMethod(
			DataFetchingEnvironment environment, EntityHandlerMethod handlerMethod,
			Map<String, Object> representation, int index) {

		return handlerMethod.getEntity(environment, representation)
				.switchIfEmpty(Mono.error(new RepresentationNotResolvedException(representation, handlerMethod)))
				.onErrorResume((ex) -> resolveException(ex, environment, handlerMethod, index));
	}

	@SuppressWarnings("NullAway") // https://github.com/uber/NullAway/issues/1290
	private Mono<EntitiesResultContainer> invokeEntitiesMethod(
			DataFetchingEnvironment environment, EntityHandlerMethod handlerMethod,
			List<Map<String, Object>> representations, String type) {

		List<Map<String, Object>> typeRepresentations = new ArrayList<>();
		List<Integer> originalIndexes = new ArrayList<>();

		for (int i = 0; i < representations.size(); i++) {
			Map<String, Object> map = representations.get(i);
			if (type.equals(map.get("__typename"))) {
				typeRepresentations.add(map);
				originalIndexes.add(i);
			}
		}

		return handlerMethod.getEntities(environment, typeRepresentations)
				.mapNotNull((result) -> (((List<?>) result).isEmpty()) ? null : result)
				.switchIfEmpty(Mono.defer(() -> {
					List<Mono<?>> exceptions = new ArrayList<>(originalIndexes.size());
					for (int i = 0; i < originalIndexes.size(); i++) {
						exceptions.add(resolveException(
								new RepresentationNotResolvedException(typeRepresentations.get(i), handlerMethod),
								environment, handlerMethod, originalIndexes.get(i)));
					}
					return Mono.zip(exceptions, Arrays::asList);
				}))
				.onErrorResume((ex) -> {
					List<Mono<?>> list = new ArrayList<>();
					for (Integer index : originalIndexes) {
						list.add(resolveException(ex, environment, handlerMethod, index));
					}
					return Mono.zip(list, Arrays::asList);
				})
				.map((result) -> new EntitiesResultContainer((List<?>) result, originalIndexes));
	}

	private Mono<ErrorContainer> resolveException(
			Throwable ex, DataFetchingEnvironment env, @Nullable EntityHandlerMethod handlerMethod, int index) {

		Throwable theEx = unwrapException(ex);
		DataFetchingEnvironment theEnv = new IndexedDataFetchingEnvironment(env, index);
		Object handler = (handlerMethod != null) ? handlerMethod.getBean() : null;

		return this.exceptionResolver.resolveException(theEx, theEnv, handler)
				.map(ErrorContainer::new)
				.switchIfEmpty(Mono.fromCallable(() -> createDefaultError(theEx, theEnv)));
	}

	private Throwable unwrapException(Throwable exception) {
		if (exception instanceof CompletionException completionException) {
			return (completionException.getCause() != null) ? completionException.getCause() : exception;
		}
		return exception;
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
			if (entity instanceof EntitiesResultContainer resultHandler) {
				resultHandler.applyResults(entities, errors);
			}
			if (entity instanceof ErrorContainer errorContainer) {
				errors.addAll(errorContainer.errors());
				entities.set(i, null);
			}
		}
		return DataFetcherResult.<List<Object>>newResult().data(entities).errors(errors).build();
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


	private record EntitiesResultContainer(List<?> results, List<Integer> originalIndexes) {

		public void applyResults(List<Object> entities, List<GraphQLError> errors) {
			for (int i = 0; i < this.results.size(); i++) {
				Object result = this.results.get(i);
				Integer index = this.originalIndexes.get(i);
				if (result instanceof ErrorContainer container) {
					errors.addAll(container.errors());
					entities.set(index, null);
				}
				else {
				entities.set(index, result);
				}
			}
		}
	}


	private record ErrorContainer(List<GraphQLError> errors) {

		ErrorContainer(GraphQLError error) {
			this(Collections.singletonList(error));
		}
	}

}
