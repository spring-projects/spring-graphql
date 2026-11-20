/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.execution.batching.recipes;


import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.dataloader.DataLoader;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Controller;

@Controller
public class FriendsControllerFiltering {

	private final Map<Integer, Person> people = Map.of(
			1, new Person(1, "Rossen", "coffee", List.of(2, 3)),
			2, new Person(2, "Brian", "tea", List.of(1, 3)),
			3, new Person(3, "Donna", "tea", List.of(1, 2, 4)),
			4, new Person(4, "Brad", "coffee", List.of(1, 2, 3, 5)),
			5, new Person(5, "Andi", "coffee", List.of(1, 2, 3, 4))
	);

	// tag::sample[]

	public FriendsControllerFiltering(BatchLoaderRegistry registry) {
		registry.forTypePair(Integer.class, Person.class).registerMappedBatchLoader((personIds, env) -> {
			Map<Integer, Person> friends = new HashMap<>();
			personIds.forEach((personId) -> friends.put(personId, this.people.get(personId))); // <1>
			return Mono.just(friends);
		});
	}

	@QueryMapping
	public Person me() {
		return /**/ this.people.get(2);
	}

	@QueryMapping
	public Collection<Person> people() {
		return /**/ this.people.values();
	}

	@SchemaMapping
	public CompletableFuture<List<Person>> friends(Person person, @Argument FriendsFilter filter, DataLoader<Integer, Person> dataLoader) {
		return dataLoader
				.loadMany(person.friendsId())
				.thenApply(filter::apply); // <2>
	}

	public record FriendsFilter(String favoriteBeverage) {

		List<Person> apply(List<Person> friends) {
			return friends.stream()
					.filter((person) -> person.favoriteBeverage.equals(this.favoriteBeverage))
					.toList();
		}
	}

	// end::sample[]

	public record Person(Integer id, String name, String favoriteBeverage, List<Integer> friendsId) {
	}

}
