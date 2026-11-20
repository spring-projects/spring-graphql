/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.controllers.exceptionhandler;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class GlobalExceptionHandler {

	@GraphQlExceptionHandler
	public GraphQLError handle(GraphqlErrorBuilder<?> errorBuilder, BindException ex) {
		return errorBuilder
				.errorType(ErrorType.BAD_REQUEST)
				.message(ex.getMessage())
				.build();
	}

}
