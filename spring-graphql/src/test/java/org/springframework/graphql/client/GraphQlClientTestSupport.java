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

package org.springframework.graphql.client;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.support.MapGraphQlResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for {@link GraphQlClient} tests.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlClientTestSupport {

	protected static final Duration TIMEOUT = Duration.ofSeconds(5);


	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


	private final ArgumentCaptor<GraphQlRequest> requestCaptor = ArgumentCaptor.forClass(GraphQlRequest.class);

	private final GraphQlTransport transport = mock(GraphQlTransport.class);

	private final GraphQlClient.Builder<?> graphQlClientBuilder = GraphQlClient.builder(this.transport);

	private final GraphQlClient graphQlClient = this.graphQlClientBuilder.build();


	protected GraphQlClient graphQlClient() {
		return this.graphQlClient;
	}

	public GraphQlClient.Builder<?> graphQlClientBuilder() {
		return this.graphQlClientBuilder;
	}

	protected GraphQlRequest request() {
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
		ExecutionResult result = builder.build();
		GraphQlResponse response = MapGraphQlResponse.forResponse(result.toSpecification());
		when(this.transport.execute(this.requestCaptor.capture())).thenReturn(Mono.just(response));
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
