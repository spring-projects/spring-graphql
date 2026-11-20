/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
 * @since 1.0.0
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
				.body(this.printer.print(this.graphQlSource.schema()));
	}

}
