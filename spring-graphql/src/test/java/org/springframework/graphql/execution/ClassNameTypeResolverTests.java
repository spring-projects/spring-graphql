/*
 * Copyright 2002-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionGraphQlService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for interface and union type resolution via {@link ClassNameTypeResolver}.
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ClassNameTypeResolverTests {

	private static final String schema = """
			type Query {
				animals: [Animal!]!,
				sightings: [Sighting!]!
			}
			interface Animal {
				name: String!
			}
			type Bird implements Animal {
				name: String!
				flightless: Boolean!
			}
			type Mammal implements Animal {
				name: String!
				herbivore: Boolean!
			}
			type Plant {
				family: String!
			}
			type Vegetable {
				family: String!
			}
			union Sighting = Bird | Mammal | Plant | Vegetable
			""";

	private static final List<Animal> animalList = new ArrayList<>();

	private static final List<Object> animalAndPlantList = new ArrayList<>();

	static {
		animalList.add(new Dog());
		animalList.add(new Penguin());

		animalAndPlantList.add(new GrayWolf());
		animalAndPlantList.add(new GiantRedwood());
	}


	private final GraphQlSetup graphQlSetup = GraphQlSetup.schemaContent(schema);


	@Test
	void resolveFromInterfaceHierarchyWithClassNames() {

		String document = """
				query Animals {
					animals {
						__typename
						name
						... on Bird {
							flightless
						}
						... on Mammal {
							herbivore
						}
					}
				}
				""";

		TestExecutionGraphQlService service =
				graphQlSetup.queryFetcher("animals", env -> animalList).toGraphQlService();

		ResponseHelper response = ResponseHelper.forResponse(service.execute(document));
		assertThat(response.errorCount()).isEqualTo(0);

		Mammal mammal = response.toEntity("animals[0]", Dog.class);
		assertThat(mammal.isHerbivore()).isEqualTo(false);

		Bird bird = response.toEntity("animals[1]", Penguin.class);
		assertThat(bird.isFlightless()).isEqualTo(true);
	}

	@Test
	void resolveWithExplicitMapping() {

		ClassNameTypeResolver typeResolver = new ClassNameTypeResolver();
		typeResolver.addMapping(Tree.class, "Plant");

		String document = """
				query Sightings {
					sightings {
						__typename
						... on Bird {
							name
						}
						... on Mammal {
							name
						}
						... on Plant {
							family
						}
					}
				}
				""";

		TestExecutionGraphQlService service =
				graphQlSetup.queryFetcher("sightings", env -> animalAndPlantList)
						.typeResolver(typeResolver)
						.toGraphQlService();

		ResponseHelper response = ResponseHelper.forResponse(service.execute(document));
		assertThat(response.errorCount()).isEqualTo(0);

		Animal animal = response.toEntity("sightings[0]", GrayWolf.class);
		assertThat(animal.getName()).isEqualTo("Gray Wolf");

		Tree tree = response.toEntity("sightings[1]", GiantRedwood.class);
		assertThat(tree.getFamily()).isEqualTo("Redwood");
	}

	@Test
	void javaTypeResolvesToSchemaInterfaceOnly() {

		String document = """
				query Animals {
					animals {
						__typename
						name
					}
				}
				""";

		TestExecutionGraphQlService service =
				graphQlSetup.queryFetcher("animals", env -> List.of(new BaseAnimal("Fox"))).toGraphQlService();

		ResponseHelper response = ResponseHelper.forResponse(service.execute(document));
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).contains("Could not determine the exact type of 'Animal'");
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
