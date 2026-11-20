/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.webflux;

import graphql.schema.idl.SchemaPrinter;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Spring WebFlux functional handler that renders the
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


	public Mono<ServerResponse> handleRequest(ServerRequest request) {
		return ServerResponse.ok()
				.contentType(MediaType.TEXT_PLAIN)
				.bodyValue(this.printer.print(this.graphQlSource.schema()));
	}

}
