/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
	 * @param request the client request
	 * @param response  the client response
	 * @param field the accessed field that caused the error
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
	 * Return the [@code GraphQlResponse} for which the error occurred.
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
