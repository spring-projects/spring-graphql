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

package org.springframework.graphql.execution;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * {@link ExecutionGraphQlService} with mock responses.
 *
 * @author Rossen Stoyanchev
 */
public class MockExecutionGraphQlService implements ExecutionGraphQlService {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


	@Nullable
	private ExecutionGraphQlRequest graphQlRequest;

	private final Map<String, ExecutionGraphQlResponse> responses = new HashMap<>();

	@Nullable
	private ExecutionGraphQlResponse defaultResponse;


	/**
	 * Get the last, saved request.
	 */
	public ExecutionGraphQlRequest getGraphQlRequest() {
		Assert.state(this.graphQlRequest != null, "No saved GraphQlRequest");
		return this.graphQlRequest;
	}


	/**
	 * Set the default response to fall back on as a "data"-only response.
	 */
	public void setDefaultResponse(String dataJson) {
		ExecutionInput input = ExecutionInput.newExecutionInput().query("").build();
		ExecutionResult result = ExecutionResultImpl.newExecutionResult().data(decode(dataJson)).build();
		this.defaultResponse = new DefaultExecutionGraphQlResponse(input, result);
	}

	/**
	 * Set a "data"-only response for the given document.
	 */
	public void setDataAsJson(String document, String dataJson) {
		setResponse(document, decode(dataJson));
	}

	/**
	 * Set an "errors" response for the given document.
	 */
	public void setErrors(String document, GraphQLError... errors) {
		setResponse(document, null, errors);
	}

	/**
	 * Set an "errors" response for the given document.
	 */
	public void setError(String document, Consumer<GraphqlErrorBuilder<?>> errorBuilderConsumer) {
		GraphqlErrorBuilder<?> errorBuilder = GraphqlErrorBuilder.newError();
		errorBuilderConsumer.accept(errorBuilder);
		setResponse(document, null, errorBuilder.build());
	}

	/**
	 * Set a "data" and "errors" response for the given document.
	 */
	public void setDataAsJsonAndErrors(String document, String dataJson, GraphQLError... errors) {
		setResponse(document, decode(dataJson), errors);
	}

	/**
	 * Set a "data" and "errors" response for the given document.
	 */
	private void setResponse(String document, @Nullable Map<String, Object> data, GraphQLError... errors) {
		ExecutionResultImpl.Builder builder = new ExecutionResultImpl.Builder();
		if (data != null) {
			builder.data(data);
		}
		if (!ObjectUtils.isEmpty(errors)) {
			builder.errors(Arrays.asList(errors));
		}
		setResponse(document, builder.build());
	}

	/**
	 * Set a response for the given document.
	 */
	@SuppressWarnings("unused")
	public void setResponse(String document, ExecutionResult result) {
		ExecutionInput input = ExecutionInput.newExecutionInput().query(document).build();
		this.responses.put(document, new DefaultExecutionGraphQlResponse(input, result));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> decode(String json) {
		try {
			return OBJECT_MAPPER.readValue(json, Map.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Override
	public Mono<ExecutionGraphQlResponse> execute(ExecutionGraphQlRequest request) {
		this.graphQlRequest = request;
		String document = request.getDocument();
		ExecutionGraphQlResponse response = this.responses.getOrDefault(document, this.defaultResponse);
		Assert.notNull(response, "Unexpected request: " + document);
		return Mono.just(response);
	}

}
