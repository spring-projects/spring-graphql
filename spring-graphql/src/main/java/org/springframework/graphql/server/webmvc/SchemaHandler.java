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
package org.springframework.graphql.server.webmvc;

import graphql.schema.idl.SchemaPrinter;

import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Spring MVC functional handler that renders the
 * {@link graphql.schema.GraphQLSchema} printed via {@link SchemaPrinter}.
 *
 * @author Rossen Stoyanchev
 */
public class SchemaHandler {

	private final GraphQlSource graphQlSource;

	private final SchemaPrinter printer = new SchemaPrinter();


	public SchemaHandler(GraphQlSource graphQlSource) {
		this.graphQlSource = graphQlSource;
	}


	public ServerResponse handleRequest(ServerRequest request) {
		return ServerResponse.ok()
				.contentType(MediaType.TEXT_PLAIN)
				.body(this.printer.print(graphQlSource.schema()));
	}

}
