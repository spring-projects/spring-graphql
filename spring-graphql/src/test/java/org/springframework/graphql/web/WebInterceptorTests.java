/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.web;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.graphql.GraphQLService;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for a {@link WebInterceptor} chain.
 */
public class WebInterceptorTests {

	@Test
	void interceptorChain() {
		StringBuilder sb = new StringBuilder();
		List<WebInterceptor> interceptors = Arrays.asList(
				new TestWebInterceptor(sb, 1), new TestWebInterceptor(sb, 2), new TestWebInterceptor(sb, 3));

		TestGraphQLService service = new TestGraphQLService();

		WebInput input = new WebInput(
				URI.create("/"), new HttpHeaders(), Collections.singletonMap("query", "any"), "1");

		WebOutput output = WebInterceptor.createHandler(interceptors, service).handle(input).block();

		assertThat(sb.toString()).isEqualTo(":pre1:pre2:pre3:post3:post2:post1");
		assertThat(output.getResponseHeaders().get("name")).containsExactly("value3", "value2", "value1");
		assertThat(service.getSavedInput().getExtensions()).containsOnlyKeys("eKey1", "eKey2", "eKey3");
	}


	private static class TestGraphQLService implements GraphQLService {

		private ExecutionInput savedInput;

		public ExecutionInput getSavedInput() {
			return this.savedInput;
		}

		@Override
		public Mono<ExecutionResult> execute(ExecutionInput input) {
			this.savedInput = input;
			return Mono.just(ExecutionResultImpl.newExecutionResult().build());
		}
	}


	private static class TestWebInterceptor implements WebInterceptor {

		private final StringBuilder output;

		private final int index;

		public TestWebInterceptor(StringBuilder output, int index) {
			this.output = output;
			this.index = index;
		}

		@Override
		public Mono<WebOutput> intercept(WebInput webInput, WebGraphQLHandler next) {

			this.output.append(":pre").append(this.index);

			webInput.configureExecutionInput((executionInput, builder) -> {
				Map<String, Object> extensions = new HashMap<>(executionInput.getExtensions());
				extensions.put("eKey" + this.index, "eValue" + this.index);
				return builder.extensions(extensions).build();
			});

			return next.handle(webInput)
					.map(output -> {
						this.output.append(":post").append(this.index);
						return output.transform(builder -> builder.responseHeader("name", "value" + this.index));
					})
					.subscribeOn(Schedulers.boundedElastic());
		}
	}

}
