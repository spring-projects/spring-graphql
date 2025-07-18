/*
 * Copyright 2002-present the original author or authors.
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

import java.util.Collections;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;


/**
 * Transport for GraphQL over HTTP requests executed with {@link RestClient}.
 *
 * @author Rossen Stoyanchev
 */
final class HttpSyncGraphQlTransport implements SyncGraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() { };


	private final RestClient restClient;

	private final MediaType contentType;


	HttpSyncGraphQlTransport(RestClient restClient) {
		Assert.notNull(restClient, "RestClient is required");
		this.restClient = restClient;
		this.contentType = initContentType(restClient);
	}

	private static MediaType initContentType(RestClient webClient) {
		HttpHeaders headers = new HttpHeaders();
		webClient.mutate().defaultHeaders(headers::putAll);
		MediaType contentType = headers.getContentType();
		return (contentType != null) ? contentType : MediaType.APPLICATION_JSON;
	}


	@Override
	@SuppressWarnings("NullAway")
	public GraphQlResponse execute(GraphQlRequest request) {

		Map<String, Object> body = this.restClient.post()
				.contentType(this.contentType)
				.accept(MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.body(request.toMap())
				.exchange((httpRequest, httpResponse) -> {
					if (httpResponse.getStatusCode().equals(HttpStatus.OK)) {
						return httpResponse.bodyTo(MAP_TYPE);
					}
					else if (httpResponse.getStatusCode().is4xxClientError() && isGraphQlResponse(httpResponse)) {
						return httpResponse.bodyTo(MAP_TYPE);
					}
					throw new HttpClientErrorException(httpResponse.getStatusCode(), httpResponse.getStatusText());
				});
		return new ResponseMapGraphQlResponse((body != null) ? body : Collections.emptyMap());
	}

	private static boolean isGraphQlResponse(ClientHttpResponse clientResponse) {
		return MediaTypes.APPLICATION_GRAPHQL_RESPONSE
				.isCompatibleWith(clientResponse.getHeaders().getContentType());
	}

}
