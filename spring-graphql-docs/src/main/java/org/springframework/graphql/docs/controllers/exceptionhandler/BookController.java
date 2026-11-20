/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.controllers.exceptionhandler;


import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindException;

@Controller
public class BookController {

	@QueryMapping
	public Book bookById(@Argument Long id) {
		return /**/ new Book();
	}

	@GraphQlExceptionHandler
	public GraphQLError handle(GraphqlErrorBuilder<?> errorBuilder, BindException ex) {
		return errorBuilder
				.errorType(ErrorType.BAD_REQUEST)
				.message(ex.getMessage())
				.build();
	}

}
