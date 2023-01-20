/*
 * Copyright 2020-2023 the original author or authors.
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.IterableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import org.springframework.beans.BeanUtils;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.validation.annotation.Validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ValidationHelper}.
 *
 * @author Brian Clozel
 */
class ValidationHelperTests {

	@Test
	void shouldFailWithNullValidator() {
		assertThatThrownBy(() -> ValidationHelper.create(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldIgnoreMethodsWithoutAnnotations() {
		Consumer<Object[]> validator = createValidator(MyBean.class, "notValidatedMethod");
		assertThat(validator).isNull();
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParams() {
		Consumer<Object[]> validator1 = createValidator(MyBean.class, "myValidMethod");
		assertViolation(() -> validator1.accept(new Object[] {null, 2}), "myValidMethod.arg0");
		assertViolation(() -> validator1.accept(new Object[] {"test", 12}), "myValidMethod.arg1");

		Consumer<Object[]> validator2 = createValidator(MyBean.class, "myValidatedParameterMethod");
		assertViolation(() -> validator2.accept(new Object[] {new ConstrainedInput(100)}), "integerValue");
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParamsWithGroups() {
		Consumer<Object[]> validator1 = createValidator(MyValidationGroupsBean.class, "myValidMethodWithGroup");
		assertViolation(() -> validator1.accept(new Object[] {null}), "myValidMethodWithGroup.arg0");

		Consumer<Object[]> validator2 = createValidator(MyValidationGroupsBean.class, "myValidMethodWithGroupOnType");
		assertViolation(() -> validator2.accept(new Object[] {null}), "myValidMethodWithGroupOnType.arg0");
	}

	@Test
	void shouldRecognizeMethodsThatRequireValidation() {
		Consumer<Object[]> validator1 = createValidator(RequiresValidationBean.class, "processConstrainedValue");
		assertThat(validator1).isNotNull();

		Consumer<Object[]> validator2 = createValidator(RequiresValidationBean.class, "processValidInput");
		assertThat(validator2).isNotNull();

		Consumer<Object[]> validator3 = createValidator(RequiresValidationBean.class, "processValidatedInput");
		assertThat(validator3).isNotNull();

		Consumer<Object[]> validator4 = createValidator(RequiresValidationBean.class, "processValue");
		assertThat(validator4).isNull();
	}

	private Consumer<Object[]> createValidator(Class<?> handlerType, String methodName) {
		return ValidationHelper.create(Validation.buildDefaultValidatorFactory().getValidator())
				.getValidationHelperFor(findHandlerMethod(handlerType, methodName));
	}

	private HandlerMethod findHandlerMethod(Class<?> handlerType, String methodName) {
		Object handler = BeanUtils.instantiateClass(handlerType);
		Method method = Arrays.stream(handlerType.getMethods())
				.filter(m -> m.getName().equals(methodName))
				.findAny()
				.orElseThrow(() -> new IllegalArgumentException("Invalid method name"));
		return new HandlerMethod(handler, method);
	}

	private IterableAssert<ConstraintViolation> assertViolations(ThrowableAssert.ThrowingCallable callable) {
		return assertThatThrownBy(callable)
				.isInstanceOf(ConstraintViolationException.class)
				.extracting("constraintViolations")
				.asInstanceOf(InstanceOfAssertFactories.iterable(ConstraintViolation.class));
	}

	private void assertViolation(ThrowableAssert.ThrowingCallable callable, String propertyPath) {
		assertViolations(callable).anyMatch(violation ->
				violation.getPropertyPath().toString().equals(propertyPath));
	}



	private static class ConstrainedInput {

		@Max(99)
		private final int integerValue;

		public ConstrainedInput(int i) {
			this.integerValue = i;
		}

		public int getIntegerValue() {
			return this.integerValue;
		}

	}


	@SuppressWarnings("unused")
	private static class MyBean {

		public String notValidatedMethod(String arg0, int arg1) {
			return "";
		}

		public Object myValidMethod(@NotNull String arg0, @Max(10) int arg1) {
			return null;
		}

		public Object myValidatedParameterMethod(@Validated ConstrainedInput input) {
			return null;
		}
	}


	interface FirstGroup {
	}


	interface SecondGroup {
	}


	@Validated(FirstGroup.class)
	@Retention(RetentionPolicy.RUNTIME)
	@interface MethodLevelGroup {
	}


	@Validated(SecondGroup.class)
	@Retention(RetentionPolicy.RUNTIME)
	@interface TypeLevelGroup {
	}


	@TypeLevelGroup
	@SuppressWarnings("unused")
	private static class MyValidationGroupsBean {

		@MethodLevelGroup
		public Object myValidMethodWithGroup(@NotNull(groups = {FirstGroup.class}) String arg0) {
			return null;
		}

		public Object myValidMethodWithGroupOnType(@NotNull(groups = {SecondGroup.class}) String arg0) {
			return null;
		}

	}


	@SuppressWarnings("unused")
	private static class RequiresValidationBean {

		public void processConstrainedValue(@Max(99) int i) {
		}

		public void processValidInput(@Valid MyInput input) {
		}

		public void processValidatedInput(@Validated MyInput input) {
		}

		public void processValue(int i) {
		}
	}


	private static class MyInput {
	}

}
