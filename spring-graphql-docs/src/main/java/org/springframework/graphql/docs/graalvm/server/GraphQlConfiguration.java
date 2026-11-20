/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.graalvm.server;

import graphql.schema.DataFetcher;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.query.QuerydslDataFetcher;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
@RegisterReflectionForBinding(Book.class) // <3>
public class GraphQlConfiguration {

	@Bean
	RuntimeWiringConfigurer customWiringConfigurer(BookRepository bookRepository) { // <1>
		DataFetcher<Book> dataFetcher = QuerydslDataFetcher.builder(bookRepository).single();
		return (wiringBuilder) -> wiringBuilder
				.type("Query", (builder) -> builder.dataFetcher("book", dataFetcher)); // <2>
	}

}
