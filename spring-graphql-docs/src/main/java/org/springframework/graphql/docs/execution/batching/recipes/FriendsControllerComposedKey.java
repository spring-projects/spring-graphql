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
public class FriendsControllerComposedKey {

	private final Map<Integer, Person> people = Map.of(
			1, new Person(1, "Rossen", "coffee", List.of(2, 3)),
			2, new Person(2, "Brian", "tea", List.of(1, 3)),
			3, new Person(3, "Donna", "tea", List.of(1, 2, 4)),
			4, new Person(4, "Brad", "coffee", List.of(1, 2, 3, 5)),
			5, new Person(5, "Andi", "coffee", List.of(1, 2, 3, 4))
	);

	// tag::sample[]
	public FriendsControllerComposedKey(BatchLoaderRegistry registry) {
		registry.forTypePair(FriendFilterKey.class, Person[].class).registerMappedBatchLoader((keys, env) -> {
			// @fold:on return dataStore.load(keys);
			Map<FriendFilterKey, Person[]> result = new HashMap<>();
			keys.forEach((key) -> { // <2>
				Person[] friends = key.person().friendsId().stream()
						.map(this.people::get)
						.filter((friend) -> key.friendsFilter().matches(friend))
						.toArray(Person[]::new);
				result.put(key, friends);
			});
			return Mono.just(result);
			// @fold:off
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
	public CompletableFuture<Person[]> friends(Person person, @Argument FriendsFilter filter, DataLoader<FriendFilterKey, Person[]> dataLoader) {
		return dataLoader.load(new FriendFilterKey(person, filter));
	}

	public record FriendsFilter(String favoriteBeverage) {
		boolean matches(Person friend) {
			return friend.favoriteBeverage.equals(this.favoriteBeverage);
		}
	}

	public record FriendFilterKey(Person person, FriendsFilter friendsFilter) { // <1>
	}

	// end::sample[]

	public record Person(Integer id, String name, String favoriteBeverage, List<Integer> friendsId) {
	}
}
