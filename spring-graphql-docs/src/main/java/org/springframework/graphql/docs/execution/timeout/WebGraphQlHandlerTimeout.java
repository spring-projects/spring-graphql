/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.docs.execution.timeout;

import java.time.Duration;

import org.springframework.graphql.execution.DefaultExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.server.TimeoutWebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler;

public class WebGraphQlHandlerTimeout {

	void configureWebGraphQlHandler() {
		GraphQlSource graphQlSource = GraphQlSource.schemaResourceBuilder().build();
		DefaultExecutionGraphQlService executionGraphQlService = new DefaultExecutionGraphQlService(graphQlSource);

		// tag::interceptor[]
		TimeoutWebGraphQlInterceptor timeoutInterceptor = new TimeoutWebGraphQlInterceptor(Duration.ofSeconds(5));
		WebGraphQlHandler webGraphQlHandler = WebGraphQlHandler
				.builder(executionGraphQlService)
				.interceptor(timeoutInterceptor)
				.build();
		GraphQlHttpHandler httpHandler = new GraphQlHttpHandler(webGraphQlHandler);
		// end::interceptor[]
	}
}
