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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.RequestInput;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link GraphQlClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultGraphQlClient implements GraphQlClient {

	private final GraphQlTransport transport;

	private final Configuration jsonPathConfig;

	private final OperationContentLoader operationContentLoader;


	public DefaultGraphQlClient(GraphQlTransport transport, Configuration jsonPathConfig,
			OperationContentLoader operationContentLoader) {

		Assert.notNull(transport, "GraphQlTransport is required");
		Assert.notNull(jsonPathConfig, "'jsonPathConfig' is required");
		Assert.notNull(operationContentLoader, "RequestNameResolver is required");
		this.transport = transport;
		this.jsonPathConfig = jsonPathConfig;
		this.operationContentLoader = operationContentLoader;
	}


	@SuppressWarnings("unchecked")
	@Override
	public <T extends GraphQlTransport> T getTransport(Class<T> requiredType) {
		return (requiredType.isInstance(this.transport) ? (T) this.transport : null);
	}

	@Override
	public RequestSpec operation(String operation) {
		return new DefaultRequestSpec(operation, this.transport, this.jsonPathConfig);
	}

	@Override
	public RequestSpec loadOperationContent(String key) {
		String requestContent = this.operationContentLoader.loadOperation(key);
		Assert.notNull(requestContent, "Failed to load operation content for key: " + key);
		return new DefaultRequestSpec(requestContent, this.transport, this.jsonPathConfig);
	}


	private static final class DefaultRequestSpec implements RequestSpec {

		private final String operation;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private final GraphQlTransport transport;

		private final Configuration jsonPathConfig;

		DefaultRequestSpec(String operation, GraphQlTransport transport, Configuration jsonPathConfig) {
			Assert.hasText(operation, "'operation' is required");
			this.operation = operation;
			this.transport = transport;
			this.jsonPathConfig = jsonPathConfig;
		}

		@Override
		public DefaultRequestSpec operationName(@Nullable String name) {
			this.operationName = name;
			return this;
		}

		@Override
		public DefaultRequestSpec variable(String name, Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public Mono<ResponseSpec> execute() {
			return this.transport.execute(initRequestInput())
					.map(payload -> new DefaultResponseSpec(payload, this.jsonPathConfig));
		}

		@Override
		public Flux<ResponseSpec> executeSubscription() {
			return this.transport.executeSubscription(initRequestInput())
					.map(payload -> new DefaultResponseSpec(payload, this.jsonPathConfig));
		}

		private RequestInput initRequestInput() {
			return new RequestInput(this.operation, this.operationName, this.variables, null, "1");
		}

	}


	private static class DefaultResponseSpec implements ResponseSpec {

		private final DocumentContext documentContext;

		private final List<GraphQLError> errors;

		private DefaultResponseSpec(ExecutionResult result, Configuration jsonPathConfig) {
			this.documentContext = JsonPath.parse(result.toSpecification(), jsonPathConfig);
			this.errors = result.getErrors();
		}

		@Override
		public <D> D toEntity(String path, Class<D> entityType) {
			return this.documentContext.read(initJsonPath(path), new TypeRefAdapter<>(entityType));
		}

		@Override
		public <D> D toEntity(String path, ParameterizedTypeReference<D> entityType) {
			return this.documentContext.read(initJsonPath(path), new TypeRefAdapter<>(entityType));
		}

		@Override
		public <D> List<D> toEntityList(String path, Class<D> elementType) {
			return this.documentContext.read(initJsonPath(path), new TypeRefAdapter<>(List.class, elementType));
		}

		@Override
		public <D> List<D> toEntityList(String path, ParameterizedTypeReference<D> elementType) {
			return this.documentContext.read(initJsonPath(path), new TypeRefAdapter<>(List.class, elementType));
		}

		private static JsonPath initJsonPath(String path) {
			if (!StringUtils.hasText(path)) {
				path = "$.data";
			}
			else if (!path.startsWith("$") && !path.startsWith("data.")) {
				path = "$.data." + path;
			}
			return JsonPath.compile(path);
		}

		@Override
		public List<GraphQLError> errors() {
			return this.errors;
		}

	}

}
