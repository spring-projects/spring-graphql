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
package org.springframework.graphql;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wrap an {@link ExecutionResult} for testing purposes. Provide data access
 * methods with JSONPath and type conversion, transparently check for no errors,
 * and provide convenience methods to verify errors.
 *
 * @author Rossen Stoyanchev
 */
public class ResponseHelper {

	private static final Log logger = LogFactory.getLog(ResponseHelper.class);


	private final DocumentContext documentContext;

	private final List<GraphQLError> errors;

	private boolean errorsChecked;


	private ResponseHelper(Map<String, Object> responseMap, List<GraphQLError> errors) {
		this.documentContext = JsonPath.parse(responseMap, initJsonPathConfig());
		this.errors = errors;
	}

	private static Configuration initJsonPathConfig() {
		return Configuration.builder()
				.jsonProvider(new JacksonJsonProvider())
				.mappingProvider(new JacksonMappingProvider())
				.build();
	}


	public ResponseHelper log() {
		logger.debug("GraphQlResponse: " + this.documentContext.jsonString());
		return this;
	}

	public <T> T toEntity(String path, Class<T> targetClass) {
		assertNoErrors();
		return this.documentContext.read(jsonPath(path), targetClass);
	}

	public <T> T toEntity(String path, ParameterizedTypeReference<T> targetType) {
		assertNoErrors();
		return this.documentContext.read(jsonPath(path), new TypeRefAdapter<>(targetType));
	}

	public <T> List<T> toList(String path, Class<T> elementClass) {
		assertNoErrors();
		return this.documentContext.read(jsonPath(path), new TypeRefAdapter<>(List.class, elementClass));
	}

	public <T> List<T> toList(String path, ParameterizedTypeReference<T> elementType) {
		assertNoErrors();
		return this.documentContext.read(jsonPath(path), new TypeRefAdapter<>(List.class, elementType));
	}

	@Nullable
	public <T> T rawValue(String path) {
		assertNoErrors();
		return this.documentContext.read(jsonPath(path));
	}

	private void assertNoErrors() {
		if (!this.errorsChecked) {
			assertThat(this.errors).as("Errors present in GraphQL response").isEmpty();
			this.errorsChecked = true;
		}
	}

	private static JsonPath jsonPath(String path) {
		if (!StringUtils.hasText(path)) {
			path = "$.data";
		}
		else if (!path.startsWith("$") && !path.startsWith("data.")) {
			path = "$.data." + path;
		}
		return JsonPath.compile(path);
	}

	public int errorCount() {
		this.errorsChecked = true;
		return this.errors.size();
	}

	public Error error(int index) {
		this.errorsChecked = true;
		return new Error(index);
	}


	public static ResponseHelper forResult(ExecutionResult result) {
		return new ResponseHelper(result.toSpecification(), result.getErrors());
	}

	public static ResponseHelper forResult(Mono<? extends ExecutionResult> resultMono) {
		ExecutionResult result = resultMono.block(Duration.ofSeconds(5));
		assertThat(result).isNotNull();
		return forResult(result);
	}

	public static ResponseHelper forResponse(Mono<? extends ExecutionGraphQlResponse> responseMono) {
		ExecutionGraphQlResponse response = responseMono.block(Duration.ofSeconds(5));
		assertThat(response).isNotNull();
		return forResult(response.getExecutionResult());
	}

	public static Flux<ResponseHelper> forSubscription(ExecutionResult result) {
		assertThat(result.getErrors()).as("Errors present in GraphQL response").isEmpty();
		Object data = result.getData();
		assertThat(data).as("Expected Publisher from subscription").isNotNull();
		assertThat(data).as("Expected Publisher from subscription").isInstanceOf(Publisher.class);
		Publisher<ExecutionResult> publisher = result.getData();
		return Flux.from(publisher).map(ResponseHelper::forResult);
	}

	@SuppressWarnings("BlockingMethodInNonBlockingContext")
	public static Flux<ResponseHelper> forSubscription(Mono<? extends ExecutionGraphQlResponse> resultMono) {
		ExecutionGraphQlResponse response = resultMono.block(Duration.ofSeconds(5));
		assertThat(response).isNotNull();
		return forSubscription(response.getExecutionResult());
	}


	public class Error {

		private final int index;

		public Error(int index) {
			this.index = index;
		}

		public String message() {
			return ResponseHelper.this.errors.get(index).getMessage();
		}

		public String errorType() {
			return ResponseHelper.this.errors.get(index).getErrorType().toString();
		}

		public Map<String, Object> extensions() {
			return ResponseHelper.this.errors.get(index).getExtensions();
		}

	}


	private static final class TypeRefAdapter<T> extends TypeRef<T> {

		private final Type type;

		TypeRefAdapter(ParameterizedTypeReference<T> typeReference) {
			this.type = typeReference.getType();
		}

		TypeRefAdapter(Class<?> clazz, Class<?> generic) {
			this.type = ResolvableType.forClassWithGenerics(clazz, generic).getType();
		}

		TypeRefAdapter(Class<?> clazz, ParameterizedTypeReference<?> generic) {
			this.type = ResolvableType.forClassWithGenerics(clazz, ResolvableType.forType(generic)).getType();
		}

		@Override
		public Type getType() {
			return this.type;
		}

	}

}
