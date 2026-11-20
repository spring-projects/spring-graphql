/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
		GraphQlHttpHandler httpHandler = GraphQlHttpHandler.builder(webGraphQlHandler).build();
		// end::interceptor[]
	}
}
