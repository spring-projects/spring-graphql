/*
 * Copyright 2020-2022 the original author or authors.
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
		return wiringBuilder -> wiringBuilder
				.type("Query", builder -> builder.dataFetcher("book", dataFetcher)); // <2>
	}

}
