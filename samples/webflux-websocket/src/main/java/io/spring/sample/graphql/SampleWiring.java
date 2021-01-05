package io.spring.sample.graphql;

import java.time.Duration;

import graphql.schema.idl.RuntimeWiring;
import reactor.core.publisher.Flux;

import org.springframework.graphql.boot.RuntimeWiringCustomizer;
import org.springframework.stereotype.Component;

@Component
public class SampleWiring implements RuntimeWiringCustomizer {

	@Override
	public void customize(RuntimeWiring.Builder builder) {
		builder.type("Query", wiringBuilder -> wiringBuilder.dataFetcher("hello",
				env -> "Hello world!"));
		builder.type("Subscription", wiringBuilder -> wiringBuilder.dataFetcher("greetings",
				env -> Flux.just("Hi", "Bonjour", "Hola", "Cio", "Zdravo")
						.delayElements(Duration.ofMillis(500))));
	}

}
