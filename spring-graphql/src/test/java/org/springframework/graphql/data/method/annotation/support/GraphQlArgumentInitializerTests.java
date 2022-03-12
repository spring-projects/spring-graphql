/*
 * Copyright 2020-2022 the original author or authors.
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
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.core.ResolvableType;
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

	private final ObjectMapper mapper = new ObjectMapper();

	private final GraphQlArgumentInitializer initializer = new GraphQlArgumentInitializer(null);


	@Test
	void shouldInstantiateDefaultConstructor() throws Exception {
		String payload = "{\"simpleBean\": { \"name\": \"test\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		Object result = initializer.initializeArgument(
				environment, "simpleBean", ResolvableType.forClass(SimpleBean.class));

		assertThat(result).isNotNull().isInstanceOf(SimpleBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void shouldInstantiatePrimaryConstructor() throws Exception {
		String payload = "{\"constructorBean\": { \"name\": \"test\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		Object result = initializer.initializeArgument(
				environment, "constructorBean", ResolvableType.forClass(ContructorBean.class));

		assertThat(result).isNotNull().isInstanceOf(ContructorBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void shouldFailIfNoPrimaryConstructor() throws Exception {
		String payload = "{\"noPrimary\": { \"name\": \"test\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		assertThatThrownBy(
				() -> {
					ResolvableType targetType = ResolvableType.forClass(NoPrimaryConstructor.class);
					initializer.initializeArgument(environment, "noPrimary", targetType);
				})
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No primary or single unique constructor found");
	}

	@Test
	void shouldInstantiateNestedBean() throws Exception {
		String payload = "{\"book\": { \"name\": \"test name\", \"author\": { \"firstName\": \"Jane\", \"lastName\": \"Spring\"} } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		Object result = initializer.initializeArgument(environment, "book", ResolvableType.forClass(Book.class));

		assertThat(result).isNotNull().isInstanceOf(Book.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test name");
		assertThat(((Book) result).getAuthor()).isNotNull()
				.hasFieldOrPropertyWithValue("firstName", "Jane")
				.hasFieldOrPropertyWithValue("lastName", "Spring");
	}

	@Test
	void shouldInstantiateNestedBeanLists() throws Exception {
		String payload = "{\"nestedList\": { \"items\": [ {\"name\": \"first\"}, {\"name\": \"second\"}] } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		Object result = initializer.initializeArgument(
				environment, "nestedList", ResolvableType.forClass(NestedList.class));

		assertThat(result).isNotNull().isInstanceOf(NestedList.class);
		assertThat(((NestedList) result).getItems()).hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test // gh-301
	void shouldInstantiateNestedBeanListsEmpty() throws Exception {
		String payload = "{\"nestedList\": { \"items\": [] } }";
		Object result = initializer.initializeArgument(
				initEnvironment(payload), "nestedList", ResolvableType.forClass(NestedList.class));

		assertThat(result).isNotNull().isInstanceOf(NestedList.class);
		assertThat(((NestedList) result).getItems()).hasSize(0);
	}

	@Test
	void shouldInstantiatePrimaryConstructorNestedBeanLists() throws Exception {
		String payload = "{\"nestedList\": { \"items\": [ {\"name\": \"first\"}, {\"name\": \"second\"}] } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		Object result = initializer.initializeArgument(
				environment, "nestedList", ResolvableType.forClass(PrimaryConstructorNestedList.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorNestedList.class);
		assertThat(((PrimaryConstructorNestedList) result).getItems())
				.hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test
	void shouldInstantiateComplexNestedBean() throws Exception {
		String payload = "{\"complex\": { \"item\": {\"name\": \"Item name\"}, \"name\": \"Hello\" } }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		Object result = initializer.initializeArgument(
				environment, "complex", ResolvableType.forClass(PrimaryConstructorComplexInput.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorComplexInput.class);
		assertThat(((PrimaryConstructorComplexInput) result).item.name).isEqualTo("Item name");
		assertThat(((PrimaryConstructorComplexInput) result).name).isEqualTo("Hello");
	}

	@SuppressWarnings("unchecked")
	private DataFetchingEnvironment initEnvironment(String jsonPayload) throws JsonProcessingException {
		Map<String, Object> arguments = this.mapper.readValue(jsonPayload, Map.class);
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