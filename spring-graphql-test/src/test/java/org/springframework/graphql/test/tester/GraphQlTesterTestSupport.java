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

package org.springframework.graphql.test.tester;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Base class for {@link GraphQlTester} tests.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlTesterTestSupport {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


	private final ArgumentCaptor<ExecutionGraphQlRequest> requestCaptor = ArgumentCaptor.forClass(ExecutionGraphQlRequest.class);

	private final GraphQlService graphQlService = mock(GraphQlService.class);

	private final GraphQlTester.Builder<?> graphQlTesterBuilder = GraphQlServiceTester.builder(this.graphQlService);

	private final GraphQlTester graphQlTester = this.graphQlTesterBuilder.build();


	protected GraphQlTester graphQlTester() {
		return this.graphQlTester;
	}

	public GraphQlTester.Builder<?> graphQlTesterBuilder() {
		return this.graphQlTesterBuilder;
	}

	protected ExecutionGraphQlRequest request() {
		return this.requestCaptor.getValue();
	}


	protected void setMockResponse(String data) {
		setMockResponse(builder -> serialize(data, builder));
	}

	protected void setMockResponse(GraphQLError... errors) {
		setMockResponse(builder -> builder.errors(Arrays.asList(errors)));
	}

	private void setMockResponse(Consumer<ExecutionResultImpl.Builder> consumer) {

		ExecutionResultImpl.Builder builder = new ExecutionResultImpl.Builder();
		consumer.accept(builder);
		ExecutionInput executionInput = ExecutionInput.newExecutionInput("{}").build();
		ExecutionResult result = builder.build();

		given(this.graphQlService.execute(this.requestCaptor.capture()))
				.willReturn(Mono.just(new DefaultExecutionGraphQlResponse(executionInput, result)));
	}

	private void serialize(String data, ExecutionResultImpl.Builder builder) {
		try {
			builder.data(OBJECT_MAPPER.readValue(data, Map.class));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
