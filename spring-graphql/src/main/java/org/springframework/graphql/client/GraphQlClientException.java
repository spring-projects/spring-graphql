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


import org.springframework.core.NestedRuntimeException;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.lang.Nullable;


/**
 * Base class for exceptions from {@code GraphQlClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class GraphQlClientException extends NestedRuntimeException {

	private final GraphQlRequest request;


	/**
	 * Constructor with a message, optional cause, and the request details.
	 */
	public GraphQlClientException(String message, @Nullable Throwable cause, GraphQlRequest request) {
		super(message, cause);
		this.request = request;
	}


	/**
	 * Return the request for which the error occurred.
	 */
	public GraphQlRequest getRequest() {
		return this.request;
	}

}
