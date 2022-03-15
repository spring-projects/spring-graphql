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


import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;

/**
 * An exception raised on an attempt to decode data from an
 * {@link GraphQlResponse#isValid() invalid response} or an
 * {@link ResponseField#isValid() invalid field}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class FieldAccessException extends GraphQlClientException {

	private final ClientGraphQlResponse response;

	private final ResponseField field;


	/**
	 * Constructor with the request and response, and the accessed field.
	 */
	public FieldAccessException(GraphQlRequest request, ClientGraphQlResponse response, ResponseField field) {
		super(initDefaultMessage(field), null, request);
		this.response = response;
		this.field = field;
	}

	private static String initDefaultMessage(ResponseField field) {
		return "Invalid field '" + field.getPath() + "', errors: " + field.getErrors();
	}


	/**
	 * Return the [@code GraphQlResponse} for which the error ouccrred.
	 */
	public ClientGraphQlResponse getResponse() {
		return this.response;
	}

	/**
	 * Return the field that needed to be accessed.
	 */
	public ResponseField getField() {
		return this.field;
	}

}
