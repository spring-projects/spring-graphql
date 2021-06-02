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
package io.spring.sample.graphql;

import graphql.schema.idl.RuntimeWiring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.boot.RuntimeWiringCustomizer;
import org.springframework.stereotype.Component;

@Component
public class SampleWiring implements RuntimeWiringCustomizer {

	private final DataRepository dataRepository;

	public SampleWiring(@Autowired DataRepository dataRepository) {
		this.dataRepository = dataRepository;
	}

	@Override
	public void customize(RuntimeWiring.Builder builder) {

		builder.type("Query", typeBuilder -> typeBuilder.dataFetcher("greeting", this.dataRepository::getBasic));

		builder.type("Query", typeBuilder -> typeBuilder.dataFetcher("greetingMono", this.dataRepository::getGreeting));

		builder.type("Query",
				typeBuilder -> typeBuilder.dataFetcher("greetingsFlux", this.dataRepository::getGreetings));

		builder.type("Subscription",
				typeBuilder -> typeBuilder.dataFetcher("greetings", this.dataRepository::getGreetingsStream));
	}

}
