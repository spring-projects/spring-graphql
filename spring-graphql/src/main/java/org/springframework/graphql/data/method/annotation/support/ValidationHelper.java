/*
 * Copyright 2002-2023 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;

import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * Holds a {@link jakarta.validation.Validator} and helps to create a validation
 * callback for a given {@link HandlerMethod} if it is determined that it
 * requires bean validation.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
class ValidationHelper {

	private final Validator validator;


	private ValidationHelper(Validator validator) {
		Assert.notNull(validator, "Validator is required");
		this.validator = validator;
	}


	/**
	 * Create a validation callback for the given {@link HandlerMethod},
	 * possibly {@code null} if the method or the method parameters do not have
	 * {@link Validated}, {@link Valid}, or {@link Constraint} annotations.
	 */
	@Nullable
	public Consumer<Object[]> getValidationHelperFor(HandlerMethod handlerMethod) {

		boolean required = false;
		Class<?>[] groups = null;

		Validated validatedAnnotation = findAnnotation(handlerMethod, Validated.class);
		if (validatedAnnotation != null) {
			required = true;
			groups = validatedAnnotation.value();
		}
		else if (findAnnotation(handlerMethod, Valid.class) != null) {
			required = true;
		}

		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			if (required) {
				break;
			}
			for (Annotation annot : parameter.getParameterAnnotations()) {
				MergedAnnotations merged = MergedAnnotations.from(annot);
				if (merged.isPresent(Valid.class) || merged.isPresent(Constraint.class) || merged.isPresent(Validated.class)) {
					required = true;
				}
			}
		}

		return (required ? new HandlerMethodValidator(handlerMethod, groups) : null);
	}

	@Nullable
	private <A extends Annotation> A findAnnotation(HandlerMethod method, Class<A> annotationType) {
		A annotation = AnnotationUtils.findAnnotation(method.getMethod(), annotationType);
		if (annotation == null) {
			annotation = AnnotationUtils.findAnnotation(method.getBeanType(), annotationType);
		}
		return annotation;
	}


	/**
	 * Factory method to create a {@link ValidationHelper} if there is a
	 * {@link Validator} bean declared, or {@code null} otherwise.
	 */
	@Nullable
	public static ValidationHelper createIfValidatorPresent(ApplicationContext context) {
		Validator validator = context.getBeanProvider(Validator.class).getIfAvailable();
		if (validator instanceof LocalValidatorFactoryBean) {
			validator = ((LocalValidatorFactoryBean) validator).getValidator();
		}
		else if (validator instanceof SpringValidatorAdapter) {
			validator = validator.unwrap(Validator.class);
		}
		return (validator != null ? create(validator) : null);
	}

	/**
	 * Factory method with a given {@link Validator} instance.
	 */
	public static ValidationHelper create(Validator validator) {
		return new ValidationHelper(validator);
	}


	/**
	 * Callback to apply validation to the invocation of a {@link HandlerMethod}.
	 */
	private class HandlerMethodValidator implements Consumer<Object[]> {

		private final HandlerMethod handlerMethod;

		@Nullable
		private final Class<?>[] validationGroups;

		private HandlerMethodValidator(HandlerMethod handlerMethod, @Nullable Class<?>[] validationGroups) {
			Assert.notNull(handlerMethod, "HandlerMethod is required");
			this.handlerMethod = handlerMethod;
			this.validationGroups = (validationGroups != null ? validationGroups : new Class<?>[] {});
		}

		@Override
		public void accept(Object[] arguments) {

			Set<ConstraintViolation<Object>> result =
					ValidationHelper.this.validator.forExecutables().validateParameters(
							this.handlerMethod.getBean(), this.handlerMethod.getMethod(), arguments, this.validationGroups);

			if (!result.isEmpty()) {
				throw new ConstraintViolationException(result);
			}
		}
	}

}
