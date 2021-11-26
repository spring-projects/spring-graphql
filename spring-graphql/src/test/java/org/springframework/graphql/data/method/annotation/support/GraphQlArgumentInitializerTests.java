/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.data.method.annotation.support;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.Book;
import org.springframework.graphql.data.GraphQlArgumentInitializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link GraphQlArgumentInitializer}
 *
 * @author Brian Clozel
 */
class GraphQlArgumentInitializerTests {

	private ObjectMapper mapper = new ObjectMapper();

	private GraphQlArgumentInitializer instantiator = new GraphQlArgumentInitializer(null);

	@Test
	void shouldInstantiateDefaultConstructor() throws Exception {
		String payload = "{\"simpleBean\": { \"name\": \"test\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		SimpleBean result = instantiator.initializeFromMap(environment.getArgument("simpleBean"), SimpleBean.class);

		assertThat(result).isNotNull().isInstanceOf(SimpleBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void shouldInstantiatePrimaryConstructor() throws Exception {
		String payload = "{\"constructorBean\": { \"name\": \"test\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		ContructorBean result = instantiator.initializeFromMap(environment.getArgument("constructorBean"), ContructorBean.class);

		assertThat(result).isNotNull().isInstanceOf(ContructorBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void shouldFailIfNoPrimaryConstructor() throws Exception {
		String payload = "{\"noPrimary\": { \"name\": \"test\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		assertThatThrownBy(() -> instantiator.initializeFromMap(environment.getArgument("noPrimary"), NoPrimaryConstructor.class))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("No primary or single unique constructor found");
	}

	@Test
	void shouldInstantiateNestedBean() throws Exception {
		String payload = "{\"book\": { \"name\": \"test name\", \"author\": { \"firstName\": \"Jane\", \"lastName\": \"Spring\"} } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		Book result = instantiator.initializeFromMap(environment.getArgument("book"), Book.class);

		assertThat(result).isNotNull().isInstanceOf(Book.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test name");
		assertThat(result.getAuthor()).isNotNull()
				.hasFieldOrPropertyWithValue("firstName", "Jane")
				.hasFieldOrPropertyWithValue("lastName", "Spring");
	}

	@Test
	void shouldInstantiateNestedBeanLists() throws Exception {
		String payload = "{\"nestedList\": { \"items\": [ {\"name\": \"first\"}, {\"name\": \"second\"}] } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		NestedList result = instantiator.initializeFromMap(environment.getArgument("nestedList"), NestedList.class);

		assertThat(result).isNotNull().isInstanceOf(NestedList.class);
		assertThat(result.getItems()).hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test
	void shouldInstantiatePrimaryConstructorNestedBeanLists() throws Exception {
		String payload = "{\"nestedList\": { \"items\": [ {\"name\": \"first\"}, {\"name\": \"second\"}] } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		PrimaryConstructorNestedList result = instantiator.initializeFromMap(environment.getArgument("nestedList"), PrimaryConstructorNestedList.class);

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorNestedList.class);
		assertThat(result.getItems()).hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test
	void shouldInstantiateComplexNestedBean() throws Exception {
		String payload = "{\"complex\": { \"item\": {\"name\": \"Item name\"}, \"name\": \"Hello\" } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		PrimaryConstructorComplexInput result = instantiator.initializeFromMap(environment.getArgument("complex"), PrimaryConstructorComplexInput.class);

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorComplexInput.class);
		assertThat(result.item.name).isEqualTo("Item name");
		assertThat(result.name).isEqualTo("Hello");
	}

	private DataFetchingEnvironment initEnvironment(String jsonPayload) throws JsonProcessingException {
		Map<String, Object> arguments = this.mapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {
		});
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();
	}

	static class SimpleBean {

		String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	static class ContructorBean {

		final String name;

		public ContructorBean(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}
	}

	static class NoPrimaryConstructor {

		NoPrimaryConstructor(String name) {
		}

		NoPrimaryConstructor(String name, Long id) {
		}
	}

	static class NestedList {

		List<Item> items;

		public List<Item> getItems() {
			return this.items;
		}

		public void setItems(List<Item> items) {
			this.items = items;
		}
	}

	static class PrimaryConstructorNestedList {

		final List<Item> items;

		public PrimaryConstructorNestedList(List<Item> items) {
			this.items = items;
		}

		public List<Item> getItems() {
			return items;
		}
	}

	static class Item {

		String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	static class PrimaryConstructorComplexInput {
		final String name;

		final Item item;

		public PrimaryConstructorComplexInput(String name, Item item) {
			this.name = name;
			this.item = item;
		}

		public String getName() {
			return this.name;
		}

		public Item getItem() {
			return item;
		}
	}
	
}