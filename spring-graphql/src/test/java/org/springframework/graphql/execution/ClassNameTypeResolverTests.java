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
package org.springframework.graphql.execution;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.TestExecutionRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for interface and union type resolution via {@link ClassNameTypeResolver}.
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ClassNameTypeResolverTests {

	private static final List<Animal> animalList = Arrays.asList(new Dog(), new Penguin());

	private static final List<?> animalAndPlantList = Arrays.asList(new GrayWolf(), new GiantRedwood());

	private static final String schema = "" +
			"type Query {" +
			"    animals: [Animal!]!," +
			"    sightings: [Sighting!]!" +
			"}" +
			"interface Animal {" +
			"    name: String!" +
			"}" +
			"type Bird implements Animal {" +
			"    name: String!" +
			"    flightless: Boolean!" +
			"}" +
			"type Mammal implements Animal {" +
			"    name: String!" +
			"    herbivore: Boolean!" +
			"}" +
			"type Plant {" +
			"    family: String!" +
			"}" +
			"type Vegetable {" +
			"    family: String!" +
			"}" +
			"union Sighting = Bird | Mammal | Plant | Vegetable ";

	private final GraphQlSetup graphQlSetup = GraphQlSetup.schemaContent(schema);


	@Test
	void typeResolutionViaSuperHierarchy() {
		String document = "" +
				"query Animals {" +
				"  animals {" +
				"    __typename" +
				"    name" +
				"    ... on Bird {" +
				"      flightless" +
				"    }" +
				"    ... on Mammal {" +
				"      herbivore" +
				"    }" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup.queryFetcher("animals", env -> animalList)
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document));

		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		for (int i = 0; i < animalList.size(); i++) {
			Animal animal = animalList.get(i);
			if (animal instanceof Bird) {
				Bird bird = (Bird) response.toEntity("animals[" + i + "]", animal.getClass());
				assertThat(bird.isFlightless()).isEqualTo(((Bird) animal).isFlightless());
			}
			else if (animal instanceof Mammal) {
				Mammal mammal = (Mammal) response.toEntity("animals[" + i + "]", animal.getClass());
				assertThat(mammal.isHerbivore()).isEqualTo(((Mammal) animal).isHerbivore());
			}
			else {
				throw new IllegalStateException();
			}
		}
	}

	@Test
	void typeResolutionViaMapping() {
		String document = "" +
				"query Sightings {" +
				"  sightings {" +
				"    __typename" +
				"    ... on Bird {" +
				"      name" +
				"    }" +
				"    ... on Mammal {" +
				"      name" +
				"    }" +
				"    ... on Plant {" +
				"      family" +
				"    }" +
				"  }" +
				"}";

		ClassNameTypeResolver typeResolver = new ClassNameTypeResolver();
		typeResolver.addMapping(Tree.class, "Plant");

		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup.queryFetcher("sightings", env -> animalAndPlantList)
				.typeResolver(typeResolver)
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document));

		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		for (int i = 0; i < animalAndPlantList.size(); i++) {
			Object sighting = animalAndPlantList.get(i);
			if (sighting instanceof Animal) {
				Animal animal = (Animal) response.toEntity("sightings[" + i + "]", sighting.getClass());
				assertThat(animal.getName()).isEqualTo(((Animal) sighting).getName());
			}
			else if (sighting instanceof Tree) {
				Tree tree = (Tree) response.toEntity("sightings[" + i + "]", sighting.getClass());
				assertThat(tree.getFamily()).isEqualTo(((Tree) sighting).getFamily());
			}
			else {
				throw new IllegalStateException();
			}
		}
	}


	interface Animal {

		String getName();

	}


	interface Bird extends Animal {

		boolean isFlightless();

	}


	interface Mammal extends Animal {

		boolean isHerbivore();

	}


	static class BaseAnimal implements Animal {

		final String name;

		BaseAnimal(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return this.name;
		}

	}


	static class BaseBird extends BaseAnimal implements Bird {

		private final boolean flightless;

		BaseBird(String name, boolean flightless) {
			super(name);
			this.flightless = flightless;
		}

		@Override
		public boolean isFlightless() {
			return this.flightless;
		}

	}


	static class BaseMammal extends BaseAnimal implements Mammal {

		private final boolean isHerbivore;

		BaseMammal(String name, boolean isHerbivore) {
			super(name);
			this.isHerbivore = isHerbivore;
		}

		@Override
		public boolean isHerbivore() {
			return this.isHerbivore;
		}

	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	static class Penguin extends BaseBird {

		Penguin() {
			super("Penguin", true);
		}

	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	static class Dog extends BaseMammal {

		Dog() {
			super("Dog", false);
		}

	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	static class GrayWolf extends BaseMammal {

		GrayWolf() {
			super("Gray Wolf", false);
		}

	}


	static class Tree {

		private final String family;

		Tree(String family) {
			this.family = family;
		}

		public String getFamily() {
			return family;
		}
	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	static class GiantRedwood extends Tree {

		GiantRedwood() {
			super("Redwood");
		}

	}

}
