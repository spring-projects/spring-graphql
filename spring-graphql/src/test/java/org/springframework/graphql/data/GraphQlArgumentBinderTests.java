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

package org.springframework.graphql.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.assertj.core.api.CollectionAssert;
import org.junit.jupiter.api.Test;

import org.springframework.core.ResolvableType;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * Tests for {@link GraphQlArgumentBinder}
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 */
class GraphQlArgumentBinderTests {

	private final ObjectMapper mapper = new ObjectMapper();

	private final GraphQlArgumentBinder binder = new GraphQlArgumentBinder(null);


	@Test
	void defaultConstructor() throws Exception {

		Object result = this.binder.bind(
				environment("{\"key\":{\"name\":\"test\"}}"), "key",
				ResolvableType.forClass(SimpleBean.class));

		assertThat(result).isNotNull().isInstanceOf(SimpleBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void defaultConstructorWithNestedBeanProperty() throws Exception {

		Object result = this.binder.bind(
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

		Object result = this.binder.bind(
				environment("{\"key\":{\"items\":[{\"name\":\"first\"},{\"name\":\"second\"}]}}"), "key",
				ResolvableType.forClass(ItemListHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ItemListHolder.class);
		assertThat(((ItemListHolder) result).getItems())
				.hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test // gh-301
	void defaultConstructorWithNestedBeanListEmpty() throws Exception {

		Object result = this.binder.bind(
				environment("{\"key\":{\"items\": []}}"), "key",
				ResolvableType.forClass(ItemListHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ItemListHolder.class);
		assertThat(((ItemListHolder) result).getItems()).hasSize(0);
	}

	@Test // gh-280
	void defaultConstructorBindingError() {

		assertThatThrownBy(
				() -> this.binder.bind(
						environment("{\"key\":{\"name\":\"test\",\"age\":\"invalid\"}}"), "key",
						ResolvableType.forClass(SimpleBean.class)))
				.extracting(ex -> ((BindException) ex).getFieldErrors())
				.satisfies(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getObjectName()).isEqualTo("key");
					assertThat(errors.get(0).getField()).isEqualTo("age");
					assertThat(errors.get(0).getRejectedValue()).isEqualTo("invalid");
				});
	}

	@Test
	void primaryConstructor() throws Exception {

		Object result = this.binder.bind(
				environment("{\"key\":{\"name\":\"test\"}}"), "key",
				ResolvableType.forClass(PrimaryConstructorBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void primaryConstructorWithBeanArgument() throws Exception {

		Object result = this.binder.bind(
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

		Object result = this.binder.bind(
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
				() -> this.binder.bind(
						environment("{\"key\":{\"name\":\"test\"}}"), "key",
						ResolvableType.forClass(NoPrimaryConstructorBean.class)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No primary or single unique constructor found");
	}

	@Test
	void primaryConstructorBindingError() {

		assertThatThrownBy(
				() -> this.binder.bind(
						environment(
								"{\"key\":{" +
										"\"name\":\"Hello\"," +
										"\"age\":\"invalid\"," +
										"\"item\":{\"name\":\"Item name\",\"age\":\"invalid\"}}}"),
						"key",
						ResolvableType.forClass(PrimaryConstructorItemBean.class)))
				.extracting(ex -> ((BindException) ex).getFieldErrors())
				.satisfies(errors -> {
					assertThat(errors).hasSize(2);

					assertThat(errors.get(0).getObjectName()).isEqualTo("key");
					assertThat(errors.get(0).getField()).isEqualTo("age");
					assertThat(errors.get(0).getRejectedValue()).isEqualTo("invalid");

					assertThat(errors.get(1).getObjectName()).isEqualTo("key");
					assertThat(errors.get(1).getField()).isEqualTo("item.age");
					assertThat(errors.get(1).getRejectedValue()).isEqualTo("invalid");
				});
	}

	@Test
	void primaryConstructorBindingErrorWithNestedBeanList() {

		assertThatThrownBy(
				() -> this.binder.bind(
						environment(
								"{\"key\":{\"items\":[" +
										"{\"name\":\"first\", \"age\":\"invalid\"}," +
										"{\"name\":\"second\", \"age\":\"invalid\"}]}}"),
						"key",
						ResolvableType.forClass(PrimaryConstructorItemListBean.class)))
				.extracting(ex -> ((BindException) ex).getFieldErrors())
				.satisfies(errors -> {
					assertThat(errors).hasSize(2);
					for (int i = 0; i < errors.size(); i++) {
						FieldError error = errors.get(i);
						assertThat(error.getObjectName()).isEqualTo("key");
						assertThat(error.getField()).isEqualTo("items[" + i + "].age");
						assertThat(error.getRejectedValue()).isEqualTo("invalid");
						assertThat(error.getDefaultMessage()).startsWith("Failed to convert property value");
					}
				});
	}

	@Test // gh-410
	@SuppressWarnings("unchecked")
	void coercionWithSingletonList() throws Exception {

		Map<String, String> itemMap = new HashMap<>();
		itemMap.put("name", "Joe");
		itemMap.put("age", "37");

		Map<String, Object> arguments = new HashMap<>();
		arguments.put("key", Collections.singletonList(itemMap));

		DataFetchingEnvironment environment =
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();

		Object result = this.binder.bind(environment, "key",
				ResolvableType.forClassWithGenerics(List.class, Item.class));

		assertThat(result).isNotNull().isInstanceOf(List.class);
		List<Item> items = (List<Item>) result;

		assertThat(items).hasSize(1);
		assertThat(items.get(0).getName()).isEqualTo("Joe");
		assertThat(items.get(0).getAge()).isEqualTo(37);
	}

	@Test // gh-392
	@SuppressWarnings("unchecked")
	void shouldHaveHigherDefaultAutoGrowLimit() throws Exception {
		String items = IntStream.range(0, 260).mapToObj(value -> "{\"name\":\"test\"}").collect(Collectors.joining(","));
		Object result = this.binder.bind(
				environment("{\"key\":{\"items\":[" + items + "]}}"), "key",
				ResolvableType.forClass(ItemListHolder.class));
		assertThat(result).isNotNull().isInstanceOf(ItemListHolder.class);
		assertThat(((ItemListHolder) result).getItems()).hasSize(260);
	}

	@Test
	void shouldUseTargetCollectionType() throws Exception {
		String items = IntStream.range(0, 5).mapToObj(value -> "{\"name\":\"test" + value + "\"}").collect(Collectors.joining(","));
		Object result = this.binder.bind(
				environment("{\"key\":{\"items\":[" + items + "]}}"), "key",
				ResolvableType.forClass(ItemSetHolder.class));
		assertThat(result).isNotNull().isInstanceOf(ItemSetHolder.class);
		assertThat(((ItemSetHolder) result).getItems()).hasSize(5);
	}

	@Test
	@SuppressWarnings("unchecked")
	void list() throws Exception {
		Object result = this.binder.bind(
				environment("{\"key\": [\"1\", \"2\", \"3\"]}"),
				"key",
				ResolvableType.forClassWithGenerics(List.class, String.class)
		);

		assertThat(result).isNotNull().isInstanceOf(List.class);
		new CollectionAssert<>((List<String>) result).containsExactly("1", "2", "3");
	}

	@Test
	@SuppressWarnings("unchecked")
	void listWithNullItem() throws Exception {
		Object result = this.binder.bind(
				environment("{\"key\": [\"1\", null, \"3\"]}"),
				"key",
				ResolvableType.forClassWithGenerics(List.class, String.class)
		);

		assertThat(result).isNotNull().isInstanceOf(List.class);
		new CollectionAssert<>((List<String>) result).containsExactly("1", null, "3");
	}

	@Test
	@SuppressWarnings("unchecked")
	void emptyList() throws Exception {
		Object result = this.binder.bind(
				environment("{\"key\": []}"),
				"key",
				ResolvableType.forClassWithGenerics(List.class, String.class)
		);

		assertThat(result).isNotNull().isInstanceOf(List.class);
		new CollectionAssert<>((List<String>) result).isEmpty();
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

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Item item = (Item) o;
			return name.equals(item.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name);
		}
	}

	static class ItemSetHolder {

		private Set<Item> items;

		public ItemSetHolder(Set<Item> items) {
			this.items = items;
		}

		public Set<Item> getItems() {
			return items;
		}

		public void setItems(Set<Item> items) {
			this.items = items;
		}
	}

}