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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

import static org.mockito.ArgumentMatchers.eq;
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


	private final ArgumentCaptor<GraphQlRequest> requestCaptor = ArgumentCaptor.forClass(DefaultGraphQlRequest.class);

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


	protected void initDataResponse(String document, String responseData) {
		initResponse(new DefaultGraphQlRequest(document), responseData);
	}

	protected void initErrorResponse(String document, GraphQLError... errors) {
		initResponse(new DefaultGraphQlRequest(document), null, errors);
	}

	protected void initResponse(String document, String responseData, GraphQLError... errors) {
		initResponse(new DefaultGraphQlRequest(document), responseData, errors);
	}

	protected void initResponse(GraphQlRequest request, @Nullable String responseData, GraphQLError... errors) {
		ExecutionResultImpl.Builder builder = new ExecutionResultImpl.Builder();
		if (responseData != null) {
			builder.data(decode(responseData));
		}
		if (!ObjectUtils.isEmpty(errors)) {
			builder.errors(Arrays.asList(errors));
		}
		ExecutionResult executionResult = builder.build();
		Map<String, Object> responseMap = executionResult.toSpecification();

		when(this.transport.execute(eq(request)))
				.thenReturn(Mono.just(GraphQlTransport.wrapResponseMap(responseMap)));
	}

	@SuppressWarnings("unchecked")
	private <T> T decode(String data) {
		try {
			return (T) OBJECT_MAPPER.readValue(data, Map.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
