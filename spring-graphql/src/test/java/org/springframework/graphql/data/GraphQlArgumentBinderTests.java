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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.lang.Nullable;
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

	private final GraphQlArgumentBinder binder = new GraphQlArgumentBinder(new DefaultFormattingConversionService());


	@Test
	void dataBinding() throws Exception {

		Object result = bind("{\"name\":\"test\"}", ResolvableType.forClass(SimpleBean.class));

		assertThat(result).isNotNull().isInstanceOf(SimpleBean.class);
		assertThat(((SimpleBean) result).getName()).isEqualTo("test");
	}

	@Test
	void dataBindingWithNestedBeanProperty() throws Exception {

		Object result = bind(
				"{\"name\":\"test name\",\"author\":{\"firstName\":\"Jane\",\"lastName\":\"Spring\"}}",
				ResolvableType.forClass(Book.class));

		assertThat(result).isNotNull().isInstanceOf(Book.class);
		Book book = (Book) result;

		assertThat(book.getName()).isEqualTo("test name");
		assertThat(book.getAuthor()).isNotNull();
		assertThat(book.getAuthor().getFirstName()).isEqualTo("Jane");
		assertThat(book.getAuthor().getLastName()).isEqualTo("Spring");
	}

	@Test
	void dataBindingWithNestedBeanListProperty() throws Exception {

		Object result = bind(
				"{\"items\":[{\"name\":\"first\"},{\"name\":\"second\"}]}",
				ResolvableType.forClass(ItemListHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ItemListHolder.class);
		ItemListHolder holder = (ItemListHolder) result;
		assertThat(holder.getItems()).hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test // gh-394
	void dataBindingWithNestedBeanSetProperty() throws Exception {

		String items = IntStream.range(0, 5)
				.mapToObj(value -> "{\"name\":\"test" + value + "\"}")
				.collect(Collectors.joining(","));

		Object result = bind("{\"items\":[" + items + "]}", ResolvableType.forClass(ItemSetHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ItemSetHolder.class);
		assertThat(((ItemSetHolder) result).getItems()).hasSize(5);
	}

	@Test // gh-301
	void dataBindingWithNestedBeanListEmpty() throws Exception {

		Object result = bind("{\"items\":[]}", ResolvableType.forClass(ItemListHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ItemListHolder.class);
		assertThat(((ItemListHolder) result).getItems()).hasSize(0);
	}

	@Test // gh-349
	void dataBindingToBeanWithEnumGenericType() throws Exception {

		Map<String, Object> argumentMap =
				Collections.singletonMap("filter", Collections.singletonMap("enums", Arrays.asList("ONE", "TWO")));

		Method method = EnumController.class.getMethod("enums", EnumInput.class);
		ResolvableType targetType = ResolvableType.forMethodParameter(new MethodParameter(method, 0));

		Object result = this.binder.bind(
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(argumentMap).build(),
				"filter", targetType);

		assertThat(result).isNotNull().isInstanceOf(EnumInput.class);
		EnumInput<FancyEnum> input = (EnumInput<FancyEnum>) result;
		assertThat(input.getEnums()).hasSize(2).containsExactly(FancyEnum.ONE, FancyEnum.TWO);
	}

	@Test // gh-280
	void dataBindingBindingError() {
		assertThatThrownBy(
				() -> bind("{\"name\":\"test\",\"age\":\"invalid\"}", ResolvableType.forClass(SimpleBean.class)))
				.satisfies(ex -> {
					List<FieldError> errors = ((BindException) ex).getFieldErrors();
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getObjectName()).isEqualTo("simpleBean");
					assertThat(errors.get(0).getField()).isEqualTo("$.age");
					assertThat(errors.get(0).getRejectedValue()).isEqualTo("invalid");
				});
	}

	@Test
	@SuppressWarnings("unchecked")
	void dataBindingToList() throws Exception {

		Object result = bind("[\"1\",\"2\",\"3\"]", ResolvableType.forClassWithGenerics(List.class, String.class));

		assertThat(result).isNotNull().isInstanceOf(List.class);
		assertThat((List<String>) result).containsExactly("1", "2", "3");

		// gh-486: List with null element
		result = bind("[\"1\",null,\"3\"]", ResolvableType.forClassWithGenerics(List.class, String.class));

		assertThat(result).isNotNull().isInstanceOf(List.class);
		assertThat((List<String>) result).containsExactly("1", null, "3");

		// Empty list

		result = bind("[]", ResolvableType.forClassWithGenerics(List.class, String.class));

		assertThat(result).isNotNull().isInstanceOf(List.class);
		assertThat((List<String>) result).isEmpty();
	}

	@Test
	void primaryConstructor() throws Exception {

		Object result = bind("{\"name\":\"test\"}", ResolvableType.forClass(PrimaryConstructorBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorBean.class);
		assertThat(result).hasFieldOrPropertyWithValue("name", "test");
	}

	@Test
	void primaryConstructorWithBeanArgument() throws Exception {

		Object result = bind(
				"{\"item\":{\"name\":\"Item name\"},\"name\":\"Hello\",\"age\":\"30\"}",
				ResolvableType.forClass(PrimaryConstructorItemBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorItemBean.class);
		PrimaryConstructorItemBean itemBean = (PrimaryConstructorItemBean) result;

		assertThat(itemBean.getItem().getName()).isEqualTo("Item name");
		assertThat(itemBean.getName()).isEqualTo("Hello");
		assertThat(itemBean.getAge()).isEqualTo(30);
	}

	@Test
	void primaryConstructorWithOptionalBeanArgument() throws Exception {

		Object result = bind(
				"{\"item\":{\"name\":\"Item name\"},\"name\":\"Hello\",\"age\":\"30\"}",
				ResolvableType.forClass(PrimaryConstructorOptionalItemBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorOptionalItemBean.class);
		PrimaryConstructorOptionalItemBean itemBean = (PrimaryConstructorOptionalItemBean) result;

		assertThat(itemBean.getItem().get().getName()).isEqualTo("Item name");
		assertThat(itemBean.getName().get()).isEqualTo("Hello");
	}

	@Test
	void primaryConstructorWithOptionalArgumentBeanArgument() throws Exception {

		ResolvableType targetType =
				ResolvableType.forClass(PrimaryConstructorOptionalArgumentItemBean.class);

		Object result = bind(
				"{\"item\":{\"name\":\"Item name\",\"age\":\"30\"},\"name\":\"Hello\"}", targetType);

		assertThat(result).isInstanceOf(PrimaryConstructorOptionalArgumentItemBean.class).isNotNull();
		PrimaryConstructorOptionalArgumentItemBean itemBean = (PrimaryConstructorOptionalArgumentItemBean) result;

		assertThat(itemBean.getItem().value().getName()).isEqualTo("Item name");
		assertThat(itemBean.getItem().value().getAge()).isEqualTo(30);
		assertThat(itemBean.getName().value()).isEqualTo("Hello");

		result = bind("{\"key\":{}}", targetType);
		itemBean = (PrimaryConstructorOptionalArgumentItemBean) result;

		assertThat(itemBean).isNotNull();
		assertThat(itemBean.getItem().isOmitted()).isFalse();
		assertThat(itemBean.getName().isOmitted()).isFalse();
	}

	@Test
	void primaryConstructorWithNestedBeanList() throws Exception {

		Object result = bind(
				"{\"items\":[{\"name\":\"first\"},{\"name\":\"second\"}]}",
				ResolvableType.forClass(PrimaryConstructorItemListBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorItemListBean.class);
		PrimaryConstructorItemListBean bean = (PrimaryConstructorItemListBean) result;
		assertThat(bean.getItems()).hasSize(2).extracting("name").containsExactly("first", "second");
	}

	@Test // gh-410
	void primaryConstructorWithNestedBeanSingletonList() throws Exception {

		Map<String, String> itemMap = new HashMap<>();
		itemMap.put("name", "Joe");
		itemMap.put("age", "37");

		Map<String, Object> arguments = new HashMap<>();
		arguments.put("key", Collections.singletonMap("items", Collections.singletonList(itemMap)));

		Object result = this.binder.bind(
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build(), "key",
				ResolvableType.forClass(PrimaryConstructorItemListBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorItemListBean.class);
		List<Item> items = ((PrimaryConstructorItemListBean) result).getItems();

		assertThat(items).hasSize(1);
		assertThat(items.get(0).getName()).isEqualTo("Joe");
		assertThat(items.get(0).getAge()).isEqualTo(37);
	}

	@Test
	void primaryConstructorNotFound() {
		assertThatThrownBy(
				() -> bind("{\"name\":\"test\"}", ResolvableType.forClass(NoPrimaryConstructorBean.class)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No primary or single unique constructor found");
	}

	@Test
	void primaryConstructorBindingError() {

		assertThatThrownBy(
				() -> bind(
						"{\"name\":\"Hello\",\"age\":\"invalid\",\"item\":{\"name\":\"Item name\",\"age\":\"invalid\"}}",
						ResolvableType.forClass(PrimaryConstructorItemBean.class)))
				.satisfies(ex -> {
					List<FieldError> fieldErrors = ((BindException) ex).getFieldErrors();
					assertThat(fieldErrors).hasSize(2);

					assertThat(fieldErrors.get(0).getObjectName()).isEqualTo("primaryConstructorItemBean");
					assertThat(fieldErrors.get(0).getField()).isEqualTo("$.age");
					assertThat(fieldErrors.get(0).getRejectedValue()).isEqualTo("invalid");

					assertThat(fieldErrors.get(1).getObjectName()).isEqualTo("primaryConstructorItemBean");
					assertThat(fieldErrors.get(1).getField()).isEqualTo("$.item.age");
					assertThat(fieldErrors.get(1).getRejectedValue()).isEqualTo("invalid");
				});
	}

	@Test
	void primaryConstructorBindingErrorWithNestedBeanList() {

		assertThatThrownBy(
				() -> bind(
						"{\"items\":[{\"name\":\"first\", \"age\":\"invalid\"},{\"name\":\"second\", \"age\":\"invalid\"}]}",
						ResolvableType.forClass(PrimaryConstructorItemListBean.class)))
				.satisfies(ex -> {
					List<FieldError> errors = ((BindException) ex).getFieldErrors();
					assertThat(errors).hasSize(2);
					for (int i = 0; i < errors.size(); i++) {
						FieldError error = errors.get(i);
						assertThat(error.getObjectName()).isEqualTo("primaryConstructorItemListBean");
						assertThat(error.getField()).isEqualTo("$.items[" + i + "].age");
						assertThat(error.getRejectedValue()).isEqualTo("invalid");
						assertThat(error.getDefaultMessage()).startsWith("Failed to convert argument value");
					}
				});
	}

	@Test
	void primaryConstructorWithMapArgument() throws Exception {

		Object result = bind(
				"{\"map\":{\"item1\":{\"name\":\"Jason\",\"age\":\"21\"},\"item2\":{\"name\":\"James\",\"age\":\"22\"}}}",
				ResolvableType.forClass(PrimaryConstructorItemMapBean.class));

		assertThat(result).isNotNull().isInstanceOf(PrimaryConstructorItemMapBean.class);
		Map<String, Item> map = ((PrimaryConstructorItemMapBean) result).getMap();

		Item item1 = map.get("item1");
		assertThat(item1.getName()).isEqualTo("Jason");
		assertThat(item1.getAge()).isEqualTo(21);

		Item item2 = map.get("item2");
		assertThat(item2.getName()).isEqualTo("James");
		assertThat(item2.getAge()).isEqualTo(22);
	}

	@Test // gh-447
	@SuppressWarnings("unchecked")
	void primaryConstructorWithGenericObject() throws Exception {

		Object result = bind(
				"{\"value\":[{\"name\":\"first\"},{\"name\":\"second\"}]}",
				ResolvableType.forClass(ObjectHolder.class));

		assertThat(result).isNotNull().isInstanceOf(ObjectHolder.class);
		ObjectHolder holder = (ObjectHolder) result;
		assertThat((List<Map<Object, Object>>) holder.getValue())
				.hasSize(2).containsExactly(
						Collections.singletonMap("name", "first"),
						Collections.singletonMap("name", "second"));
	}

	@Test // gh-349
	void primaryConstructorWithEnumGenericType() throws Exception {

		Map<String, Object> argumentMap =
				Collections.singletonMap("filter", Collections.singletonMap("enums", Arrays.asList("ONE", "TWO")));

		Method method = EnumController.class.getMethod("enums", ConstructorEnumInput.class);
		ResolvableType targetType = ResolvableType.forMethodParameter(new MethodParameter(method, 0));

		Object result = this.binder.bind(
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(argumentMap).build(),
				"filter", targetType);

		assertThat(result).isNotNull().isInstanceOf(ConstructorEnumInput.class);
		ConstructorEnumInput<FancyEnum> input = (ConstructorEnumInput<FancyEnum>) result;
		assertThat(input.enums()).hasSize(2).containsExactly(FancyEnum.ONE, FancyEnum.TWO);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private Object bind(String json, ResolvableType targetType) throws Exception {
		DataFetchingEnvironment environment =
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
						.arguments(this.mapper.readValue("{\"key\":" + json + "}", Map.class))
						.build();
		return this.binder.bind(environment, "key", targetType);
	}


	@SuppressWarnings("unused")
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


	static class PrimaryConstructorItemMapBean {

		private final Map<String, Item> map;

		public PrimaryConstructorItemMapBean(Map<String, Item> map) {
			this.map = map;
		}

		public Map<String, Item> getMap() {
			return this.map;
		}
	}


	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	static class PrimaryConstructorOptionalItemBean {

		private final Optional<String> name;

		private final Optional<Item> item;

		public PrimaryConstructorOptionalItemBean(Optional<String> name, Optional<Item> item) {
			this.name = name;
			this.item = item;
		}

		public Optional<String> getName() {
			return this.name;
		}

		public Optional<Item> getItem() {
			return item;
		}
	}


	static class PrimaryConstructorOptionalArgumentItemBean {

		private final ArgumentValue<String> name;

		private final ArgumentValue<Item> item;

		public PrimaryConstructorOptionalArgumentItemBean(ArgumentValue<String> name, ArgumentValue<Item> item) {
			this.name = name;
			this.item = item;
		}

		public ArgumentValue<String> getName() {
			return this.name;
		}

		public ArgumentValue<Item> getItem() {
			return item;
		}
	}


	@SuppressWarnings("unused")
	static class NoPrimaryConstructorBean {

		NoPrimaryConstructorBean(String name) {
		}

		NoPrimaryConstructorBean(String name, Long id) {
		}
	}


	@SuppressWarnings("unused")
	static class ItemListHolder {

		private List<Item> items;

		public List<Item> getItems() {
			return this.items;
		}

		public void setItems(List<Item> items) {
			this.items = items;
		}
	}


	@SuppressWarnings("unused")
	static class ItemSetHolder {

		private Set<Item> items;

		public Set<Item> getItems() {
			return items;
		}

		public void setItems(Set<Item> items) {
			this.items = items;
		}
	}


	static class ObjectHolder {

		private final Object value;

		ObjectHolder(Object value) {
			this.value = value;
		}

		public Object getValue() {
			return value;
		}
	}


	@SuppressWarnings("unused")
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


	static class EnumController {

		public List<FancyEnum> enums(@Argument EnumInput<FancyEnum> filter) {
			return filter.getEnums();
		}

		public List<FancyEnum> enums(@Argument ConstructorEnumInput<FancyEnum> filter) {
			return filter.enums();
		}
	}


	enum FancyEnum {
		ONE, TWO, THREE
	}


	static class EnumInput<E extends Enum<E>> {

		private List<E> enums;

		public List<E> getEnums() {
			return enums;
		}

		public void setEnums(List<E> enums) {
			this.enums = enums;
		}
	}


	record ConstructorEnumInput<E extends Enum<E>>(List<E> enums) {
	}

}