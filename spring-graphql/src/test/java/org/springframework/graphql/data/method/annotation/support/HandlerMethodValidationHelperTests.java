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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

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
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HandlerMethodValidationHelper}.
 * @author Brian Clozel
 */
class HandlerMethodValidationHelperTests {

	private final HandlerMethodValidationHelper validator =
			new HandlerMethodValidationHelper(Validation.buildDefaultValidatorFactory().getValidator());


	@Test
	void shouldFailWithNullValidator() {
		assertThatThrownBy(() -> new HandlerMethodValidationHelper(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldIgnoreMethodsWithoutAnnotations() {
		HandlerMethod method = findHandlerMethod(MyBean.class, "notValidatedMethod");
		assertThatNoException().isThrownBy(() -> validator.validate(method, new Object[] {"test", 12}));
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParams() {
		HandlerMethod method = findHandlerMethod(MyBean.class, "myValidMethod");
		assertViolations(() -> validator.validate(method, new Object[] {null, 2}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethod.arg0"));
		assertViolations(() -> validator.validate(method, new Object[] {"test", 12}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethod.arg1"));
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParamsWithGroups() {
		HandlerMethod myValidMethodWithGroup = findHandlerMethod(MyValidationGroupsBean.class, "myValidMethodWithGroup");
		assertViolations(() -> validator.validate(myValidMethodWithGroup, new Object[] {null}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethodWithGroup.arg0"));

		HandlerMethod myValidMethodWithGroupOnType = findHandlerMethod(MyValidationGroupsBean.class, "myValidMethodWithGroupOnType");
		assertViolations(() -> validator.validate(myValidMethodWithGroupOnType, new Object[] {null}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethodWithGroupOnType.arg0"));
	}

	@Test
	void shouldRecognizeMethodsThatRequireValidation() {
		HandlerMethod method = findHandlerMethod(RequiresValidationBean.class, "processConstrainedValue");
		assertThat(validator.requiresValidation(method)).isTrue();

		method = findHandlerMethod(RequiresValidationBean.class, "processValidInput");
		assertThat(validator.requiresValidation(method)).isTrue();

		method = findHandlerMethod(RequiresValidationBean.class, "processValidatedInput");
		assertThat(validator.requiresValidation(method)).isTrue();

		method = findHandlerMethod(RequiresValidationBean.class, "processInputWithConstrainedValue");
		assertThat(validator.requiresValidation(method)).isTrue();

		method = findHandlerMethod(RequiresValidationBean.class, "processOptionalInputWithConstrainedValue");
		assertThat(validator.requiresValidation(method)).isTrue();

		method = findHandlerMethod(RequiresValidationBean.class, "processValue");
		assertThat(validator.requiresValidation(method)).isFalse();
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


	@SuppressWarnings("unused")
	private static class MyBean {

		public String notValidatedMethod(String arg0, int arg1) {
			return "";
		}

		public Object myValidMethod(@NotNull String arg0, @Max(10) int arg1) {
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

		public void processInputWithConstrainedValue(MyConstrainedInput input) {
		}

		public void processOptionalInputWithConstrainedValue(Optional<MyConstrainedInput> input) {
		}

		public void processValue(int i) {
		}

	}


	private static class MyInput {
	}

	private static class MyConstrainedInput {

		@Max(99)
		private int i;

		public int getI() {
			return this.i;
		}

		public void setI(int i) {
			this.i = i;
		}

	}

}
