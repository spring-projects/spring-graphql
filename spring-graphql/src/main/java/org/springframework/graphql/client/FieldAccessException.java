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


import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseField;

/**
 * An exception raised when an attempt is made to decode data from a response
 * that is not {@link GraphQlResponse#isValid() valid} or where field value
 * is {@code null}, or there field {@link ResponseField#getErrors() errors}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class FieldAccessException extends GraphQlClientException {

	private final ClientGraphQlResponse response;

	private final ClientResponseField field;


	/**
	 * Constructor with the request and response, and the accessed field.
	 */
	public FieldAccessException(
			ClientGraphQlRequest request, ClientGraphQlResponse response, ClientResponseField field) {

		super(initDefaultMessage(field, response), null, request);
		this.response = response;
		this.field = field;
	}

	private static String initDefaultMessage(ClientResponseField field, ClientGraphQlResponse response) {
		return "Invalid field '" + field.getPath() + "', errors: " +
				(!field.getErrors().isEmpty() ? field.getErrors() : response.getErrors());
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
	public ClientResponseField getField() {
		return this.field;
	}

}
