/*
 * Copyright 2002-2022 the original author or authors.
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Repository;

/**
 * Repository with data fetcher methods.
 */
@Repository
public class DataRepository {

	public String getBasic() {
		return "Hello world!";
	}

	public Mono<String> getGreeting() {
		return Mono.delay(Duration.ofMillis(50)).map(aLong -> "Hello!");
	}

	public Flux<String> getGreetings() {
		return Mono.delay(Duration.ofMillis(50))
				.flatMapMany(aLong -> Flux.just("Hi!", "Bonjour!", "Hola!", "Ciao!", "Zdravo!"));
	}

	public Flux<String> getGreetingsStream() {
		return Mono.delay(Duration.ofMillis(50))
				.flatMapMany(aLong -> Flux.just("Hi!", "Bonjour!", "Hola!", "Ciao!", "Zdravo!"));
	}

}
