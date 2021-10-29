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
package org.springframework.graphql.test.tester;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.GraphQLError;
import reactor.core.publisher.Flux;

import org.springframework.graphql.web.WebInput;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;

/**
 * {@link WebRequestStrategy} that uses {@link WebTestClient} to perform
 * requests. Depending on how the client is configured, this may be used with
 * Spring MVC and WebFlux controllers, without a server, or against a live server.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class WebTestClientRequestStrategy extends RequestStrategySupport implements WebRequestStrategy {

	private final WebTestClient client;


	WebTestClientRequestStrategy(WebTestClient client,
			@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration responseTimeout) {

		super(errorFilter, jsonPathConfig, responseTimeout);
		this.client = client;
	}


	@Override
	public WebGraphQlTester.WebResponseSpec execute(WebInput webInput) {
		EntityExchangeResult<byte[]> result = this.client.post()
				.contentType(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.putAll(webInput.getHeaders()))
				.bodyValue(webInput.toMap())
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.contentType(MediaType.APPLICATION_JSON)
				.expectBody()
				.returnResult();

		byte[] bytes = result.getResponseBodyContent();
		Assert.notNull(bytes, "Expected GraphQL response content");
		String content = new String(bytes, StandardCharsets.UTF_8);

		DocumentContext documentContext = JsonPath.parse(content, getJsonPathConfig());
		GraphQlTester.ResponseSpec responseSpec = createResponseSpec(documentContext, result::assertWithDiagnostics);
		return DefaultWebGraphQlTester.createResponseSpec(responseSpec, result.getResponseHeaders());
	}

	@Override
	public WebGraphQlTester.WebSubscriptionSpec executeSubscription(WebInput webInput) {
		FluxExchangeResult<TestExecutionResult> exchangeResult = this.client.post()
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.headers(headers -> {
					Locale locale = webInput.getLocale();
					if (locale != null) {
						headers.setAcceptLanguageAsLocales(Collections.singletonList(locale));
					}
					headers.putAll(webInput.getHeaders());
				})
				.bodyValue(webInput.toMap())
				.exchange()
				.expectStatus()
				.isOk()
				.expectHeader()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.returnResult(TestExecutionResult.class);

		Flux<GraphQlTester.ResponseSpec> flux = exchangeResult.getResponseBody()
				.map((result) -> createResponseSpec(result, exchangeResult::assertWithDiagnostics));

		return DefaultWebGraphQlTester.createSubscriptionSpec(() -> flux, exchangeResult.getResponseHeaders());
	}

}
