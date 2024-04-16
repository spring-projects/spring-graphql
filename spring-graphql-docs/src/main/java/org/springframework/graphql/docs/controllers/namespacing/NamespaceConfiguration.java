/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.docs.controllers.namespacing;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class NamespaceConfiguration {

	@Bean
	public GraphQlSourceBuilderCustomizer customizer() {
		List<String> queryWrappers = List.of("music", "users"); // <1>

		return (sourceBuilder) -> sourceBuilder.configureRuntimeWiring((wiringBuilder) ->
				queryWrappers.forEach((field) -> wiringBuilder.type("Query",
						(builder) -> builder.dataFetcher(field, (env) -> Collections.emptyMap()))) // <2>
		);
	}

}
