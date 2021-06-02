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

import java.time.Duration;

import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Repository;

/**
 * Repository with data fetcher methods.
 */
@Repository
public class DataRepository {

	public String getBasic(DataFetchingEnvironment environment) {
		return "Hello world!";
	}

	public Mono<String> getGreeting(DataFetchingEnvironment environment) {
		return Mono.deferContextual(context -> {
			Object name = context.get("name");
			return Mono.delay(Duration.ofMillis(50)).map(aLong -> "Hello " + name);
		});
	}

	public Flux<String> getGreetings(DataFetchingEnvironment environment) {
		return Mono.delay(Duration.ofMillis(50)).flatMapMany(aLong -> Flux.deferContextual(context -> {
			String name = context.get("name");
			return Flux.just("Hi", "Bonjour", "Hola", "Ciao", "Zdravo").map(s -> s + " " + name);
		}));
	}

	public Flux<String> getGreetingsStream(DataFetchingEnvironment environment) {
		return Mono.delay(Duration.ofMillis(50)).flatMapMany(aLong -> Flux.deferContextual(context -> {
			String name = context.get("name");
			return Flux.just("Hi", "Bonjour", "Hola", "Ciao", "Zdravo").map(s -> s + " " + name);
		}));
	}

}
