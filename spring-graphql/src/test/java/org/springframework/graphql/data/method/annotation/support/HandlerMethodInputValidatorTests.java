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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.IterableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import org.springframework.beans.BeanUtils;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.validation.annotation.Validated;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HandlerMethodInputValidator}
 * @author Brian Clozel
 */
class HandlerMethodInputValidatorTests {

	private final HandlerMethodInputValidator validator = new HandlerMethodInputValidator();

	@Test
	void shouldFailWithNullValidator() {
		assertThatThrownBy(() -> new HandlerMethodInputValidator(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldIgnoreMethodsWithoutAnnotations() throws Exception {
		HandlerMethod method = findHandlerMethod(MyValidBean.class, "notValidatedMethod");
		assertThatNoException().isThrownBy(() -> validator.validate(method, new Object[] {"test", 12}));
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParams() throws Exception {
		HandlerMethod method = findHandlerMethod(MyValidBean.class, "myValidMethod");
		assertViolations(() -> validator.validate(method, new Object[] {null, 2}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethod.arg0"));
		assertViolations(() -> validator.validate(method, new Object[] {"test", 12}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethod.arg1"));
	}

	@Test
	void shouldRaiseValidationErrorForAnnotatedParamsWithGroups() throws Exception {
		HandlerMethod myValidMethodWithGroup = findHandlerMethod(MyValidBeanWithGroup.class, "myValidMethodWithGroup");
		assertViolations(() -> validator.validate(myValidMethodWithGroup, new Object[] {null}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethodWithGroup.arg0"));
		HandlerMethod myValidMethodWithGroupOnType = findHandlerMethod(MyValidBeanWithGroup.class, "myValidMethodWithGroupOnType");
		assertViolations(() -> validator.validate(myValidMethodWithGroupOnType, new Object[] {null}))
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("myValidMethodWithGroupOnType.arg0"));
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

	public static class MyValidBean {

		public String notValidatedMethod(String arg0, int arg1) {
			return "";
		}

		public Object myValidMethod(@NotNull String arg0, @Max(10) int arg1) {
			return null;
		}

	}

	public interface FirstGroup {
	}


	public interface SecondGroup {
	}

	@Validated(FirstGroup.class)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface GroupOnParam {
	}

	@Validated(SecondGroup.class)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface GroupOnType {
	}

	@GroupOnType
	public static class MyValidBeanWithGroup {

		@GroupOnParam
		public Object myValidMethodWithGroup(@NotNull(groups = {FirstGroup.class}) String arg0) {
			return null;
		}

		public Object myValidMethodWithGroupOnType(@NotNull(groups = {SecondGroup.class}) String arg0) {
			return null;
		}

	}

}