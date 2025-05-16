/*
 * Copyright 2020-2025 the original author or authors.
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
