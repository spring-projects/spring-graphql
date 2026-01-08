/*
 * Copyright 2020-present the original author or authors.
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
import java.util.function.BiConsumer;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.IterableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import org.springframework.beans.BeanUtils;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
	void shouldReturnNullValidatorWhenInstantiationFailure() {
		OptionalValidatorFactoryBean validatorFactory = mock(OptionalValidatorFactoryBean.class);
		given(validatorFactory.getValidator()).willThrow(new IllegalStateException("No target ValidatorFactory set"));
		StaticApplicationContext applicationContext = new StaticApplicationContext();
		applicationContext.registerBean(Validator.class, () -> validatorFactory);
		assertThat(ValidationHelper.createIfValidatorPresent(applicationContext)).isNull();
	}

	@Test
	void shouldIgnoreMethodsWithoutAnnotations() {
		assertThat(validateFunction(MyBean.class, "notValidatedMethod")).isNull();
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParams() {
		MyBean bean = new MyBean();

		BiConsumer<Object, Object[]> validator1 = validateFunction(MyBean.class, "myValidMethod");
		assertViolation(() -> validator1.accept(bean, new Object[] {null, 2}), "myValidMethod.arg0");
		assertViolation(() -> validator1.accept(bean, new Object[] {"test", 12}), "myValidMethod.arg1");

		BiConsumer<Object, Object[]> validator2 = validateFunction(MyBean.class, "myValidatedParameterMethod");
		assertViolation(() -> validator2.accept(bean, new Object[] {new ConstrainedInput(100)}), "integerValue");

		BiConsumer<Object, Object[]> validator3 = validateFunction(MyBean.class, "myValidArgumentValue");
		assertViolation(() -> validator3.accept(bean, new Object[] {ArgumentValue.ofNullable("")}), "myValidArgumentValue.arg0");

		// Validate that an explicit null value is validated.
		assertViolation(() -> validator3.accept(bean, new Object[] {ArgumentValue.ofNullable(null)}), "myValidArgumentValue.arg0");
	}

	@Test
	void shouldNotRaiseValidationErrorForOmittedArgumentValue() {
		MyBean bean = new MyBean();

		// Validate that an omitted value is allowed.
		BiConsumer<Object, Object[]> validator3 = validateFunction(MyBean.class, "myValidArgumentValue");
		validator3.accept(bean, new Object[] {ArgumentValue.omitted()});
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParamsWithGroups() {
		MyValidationGroupsBean bean = new MyValidationGroupsBean();

		BiConsumer<Object, Object[]> validator1 = validateFunction(MyValidationGroupsBean.class, "myValidMethodWithGroup");
		assertViolation(() -> validator1.accept(bean, new Object[] {null}), "myValidMethodWithGroup.arg0");

		BiConsumer<Object, Object[]> validator2 = validateFunction(MyValidationGroupsBean.class, "myValidMethodWithGroupOnType");
		assertViolation(() -> validator2.accept(bean, new Object[] {null}), "myValidMethodWithGroupOnType.arg0");
	}

	@Test
	void shouldRecognizeMethodsThatRequireValidation() {
		BiConsumer<Object, Object[]> validator1 = validateFunction(RequiresValidationBean.class, "processConstrainedValue");
		assertThat(validator1).isNotNull();

		BiConsumer<Object, Object[]> validator2 = validateFunction(RequiresValidationBean.class, "processValidInput");
		assertThat(validator2).isNotNull();

		BiConsumer<Object, Object[]> validator3 = validateFunction(RequiresValidationBean.class, "processValidatedInput");
		assertThat(validator3).isNotNull();

		BiConsumer<Object, Object[]> validator4 = validateFunction(RequiresValidationBean.class, "processValue");
		assertThat(validator4).isNull();
	}

	private BiConsumer<Object, Object[]> validateFunction(Class<?> handlerType, String methodName) {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		ValidationHelper helper = ValidationHelper.create(validator);
		return helper.getValidationHelperFor(findHandlerMethod(handlerType, methodName));
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

		public Object myValidArgumentValue(@Valid ArgumentValue<@NotBlank String> arg0) {
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
