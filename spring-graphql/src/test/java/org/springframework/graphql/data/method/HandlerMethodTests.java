/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.data.method;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HandlerMethod}.
 *
 * @author Brian Clozel
 */
class HandlerMethodTests {


	@Test
	void shouldFindArgumentAnnotationInGenericInterface() {
		Object target = new GenericInterfaceImpl();

		HandlerMethod handlerMethod1 = getHandlerMethod(target, "fetchOneArg", Long.class);
		MethodParameter[] methodParameters1 = handlerMethod1.getMethodParameters();
		assertThat(methodParameters1).hasSize(1);
		Annotation[] parameterAnnotations1 = methodParameters1[0].getParameterAnnotations();
		assertThat(parameterAnnotations1).hasSize(1);
		assertThat(parameterAnnotations1[0].annotationType()).isEqualTo(Argument.class);

		HandlerMethod handlerMethod2 = getHandlerMethod(target, "fetchTwoArg", Long.class, Object.class);
		MethodParameter[] methodParameters2 = handlerMethod2.getMethodParameters();
		assertThat(methodParameters2).hasSize(2);
		Annotation[] parameterAnnotations2_1 = methodParameters2[0].getParameterAnnotations();
		assertThat(parameterAnnotations2_1).hasSize(1);
		assertThat(parameterAnnotations2_1[0].annotationType()).isEqualTo(Argument.class);
		Annotation[] parameterAnnotations2_2 = methodParameters2[1].getParameterAnnotations();
		assertThat(parameterAnnotations2_2).hasSize(1);
		assertThat(parameterAnnotations2_2[0].annotationType()).isEqualTo(Argument.class);
	}


	private static HandlerMethod getHandlerMethod(Object target, String methodName, Class<?>... parameterTypes) {
		Method method = ClassUtils.getMethod(target.getClass(), methodName, parameterTypes);
		return new HandlerMethod(target, method);
	}


	interface GenericInterface<A, B> {

		void fetchOneArg(@Argument A arg1);

		void fetchTwoArg(@Argument A arg1, @Argument B arg2);
	}

	abstract static class GenericAbstractSuperclass<C> implements GenericInterface<Long, C> {

		@Override
		public void fetchOneArg(Long arg1) {

		}

		@Override
		public void fetchTwoArg(Long arg1, C arg2) {

		}

		public abstract void fetchTwoArg(@Argument C arg);
	}

	static class GenericInterfaceImpl extends GenericAbstractSuperclass<String> {

		@Override
		public void fetchTwoArg(String arg) {

		}
	}

}
