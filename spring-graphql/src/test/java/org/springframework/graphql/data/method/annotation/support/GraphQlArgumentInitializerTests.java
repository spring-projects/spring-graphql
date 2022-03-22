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
 * @author Rossen Stoyanchev
 */
class GraphQlArgumentInitializerTests {

	private final ObjectMapper mapper = new ObjectMapper();

	private final ThreadLocal<GraphQlArgumentInitializer> initializer = ThreadLocal.withInitial(() -> new GraphQlArgumentInitializer(null));


	@Test
	void defaultConstructor() throws Exception {

		Object result = initializer.get().initializeArgument(
				environment("{\"key\":{\"name\":\"test\"}}"), "key",
				ResolvableType.forClass(SimpleBean.class));

		assertThat(result).isNotNull().isInstanceOf(SimpleBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void defaultConstructorWithNestedBeanProperty() throws Exception {

		Object result = initializer.get().initializeArgument(
				environment(
						"{\"key\":{" +
								"\"name\":\"test name\"," +
								"\"author\":{" +
								"  \"firstName\":\"Jane\"," +
								"  \"lastName\":\"Spring\"" +
								"}}}"),
				"key",
				ResolvableType.forClass(Book.class));

		assertThat(result).isNotNull().isInstanceOf(Book.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test name");
		assertThat(((Book) result).getAuthor()).isNotNull()
				.hasFieldOrPropertyWithValue("firstName", "Jane")
				.hasFieldOrPropertyWithValue("lastName", "Spring");
	}

	@Test
	void defaultConstructorWithNestedBeanListProperty() throws Exception {

		Object result = initializer.get().initializeArgument(
				environment("{\"key\":{\"items\":[{\"name\":\"first\"},{\"name\":\"second\"}]}}"), "key",
				ResolvableType.forClass(ItemListHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ItemListHolder.class);
		assertThat(((ItemListHolder) result).getItems())
				.hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test // gh-301
	void defaultConstructorWithNestedBeanListEmpty() throws Exception {

		Object result = initializer.get().initializeArgument(
				environment("{\"key\":{\"items\": []}}"), "key",
				ResolvableType.forClass(ItemListHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ItemListHolder.class);
		assertThat(((ItemListHolder) result).getItems()).hasSize(0);
	}

	@Test
	void primaryConstructor() throws Exception {

		Object result = initializer.get().initializeArgument(
				environment("{\"key\":{\"name\":\"test\"}}"), "key",
				ResolvableType.forClass(PrimaryConstructorBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void primaryConstructorWithBeanArgument() throws Exception {

		Object result = initializer.get().initializeArgument(
				environment(
						"{\"key\":{" +
								"\"item\":{\"name\":\"Item name\"}," +
								"\"name\":\"Hello\"," +
								"\"age\":\"30\"}}"),
				"key",
				ResolvableType.forClass(PrimaryConstructorItemBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorItemBean.class);
		assertThat(((PrimaryConstructorItemBean) result).getItem().getName()).isEqualTo("Item name");
		assertThat(((PrimaryConstructorItemBean) result).getName()).isEqualTo("Hello");
		assertThat(((PrimaryConstructorItemBean) result).getAge()).isEqualTo(30);
	}

	@Test
	void primaryConstructorWithNestedBeanList() throws Exception {

		Object result = initializer.get().initializeArgument(
				environment(
						"{\"key\":{\"items\":[" +
								"{\"name\":\"first\"}," +
								"{\"name\":\"second\"}]}}"),
				"key",
				ResolvableType.forClass(PrimaryConstructorItemListBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorItemListBean.class);
		assertThat(((PrimaryConstructorItemListBean) result).getItems())
				.hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test
	void primaryConstructorNotFound() {
		assertThatThrownBy(
				() -> initializer.get().initializeArgument(
						environment("{\"key\":{\"name\":\"test\"}}"), "key",
						ResolvableType.forClass(NoPrimaryConstructorBean.class)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No primary or single unique constructor found");
	}

	@SuppressWarnings("unchecked")
	private DataFetchingEnvironment environment(String jsonPayload) throws JsonProcessingException {
		Map<String, Object> arguments = this.mapper.readValue(jsonPayload, Map.class);
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();
	}


	static class SimpleBean {

		private String name;

		private int age;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getAge() {
			return this.age;
		}

		public void setAge(int age) {
			this.age = age;
		}
	}


	static class PrimaryConstructorBean {

		private final String name;

		public PrimaryConstructorBean(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}
	}


	static class PrimaryConstructorItemBean {

		private final String name;

		private final int age;

		private final Item item;

		public PrimaryConstructorItemBean(String name, int age, Item item) {
			this.name = name;
			this.age = age;
			this.item = item;
		}

		public String getName() {
			return this.name;
		}

		public int getAge() {
			return this.age;
		}

		public Item getItem() {
			return item;
		}
	}


	static class PrimaryConstructorItemListBean {

		private final List<Item> items;

		public PrimaryConstructorItemListBean(List<Item> items) {
			this.items = items;
		}

		public List<Item> getItems() {
			return items;
		}
	}


	static class NoPrimaryConstructorBean {

		NoPrimaryConstructorBean(String name) {
		}

		NoPrimaryConstructorBean(String name, Long id) {
		}
	}


	static class ItemListHolder {

		private List<Item> items;

		public List<Item> getItems() {
			return this.items;
		}

		public void setItems(List<Item> items) {
			this.items = items;
		}
	}


	static class Item {

		private String name;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

}