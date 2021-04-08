/*
 * Copyright 2020-2021 the original author or authors.
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

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Container for the input of a GraphQL query over HTTP. The input includes the
 * {@link UriComponents URL} and the headers of the request, as well as the
 * query name, operation name, and variables from the request body.
 */
public class WebInput extends RequestInput {

	private final UriComponents uri;

	private final HttpHeaders headers;


	public WebInput(URI uri, HttpHeaders headers, Map<String, Object> body) {
		super(body);
		Assert.notNull(uri, "URI is required'");
		Assert.notNull(headers, "HttpHeaders is required'");
		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
	}


	/**
	 * Return the URI of the HTTP request including
	 * {@link UriComponents#getQueryParams() query parameters}.
	 */
	public UriComponents uri() {
		return this.uri;
	}

	/**
	 * Return the headers of the request.
	 */
	public HttpHeaders headers() {
		return this.headers;
	}

}