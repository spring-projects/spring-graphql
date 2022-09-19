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

import java.lang.annotation.Annotation;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.metadata.BeanDescriptor;

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
 * Helper class to apply standard bean validation to a {@link HandlerMethod}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0
 */
final class HandlerMethodValidationHelper {

	private final Validator validator;


	/**
	 * Constructor with the {@link Validator} instance to use.
	 */
	public HandlerMethodValidationHelper(Validator validator) {
		Assert.notNull(validator, "validator should not be null");
		if (validator instanceof LocalValidatorFactoryBean) {
			this.validator = ((LocalValidatorFactoryBean) validator).getValidator();
		}
		else if (validator instanceof SpringValidatorAdapter) {
			this.validator = validator.unwrap(Validator.class);
		}
		else {
			this.validator = validator;
		}
	}


	/**
	 * Validate the input values to a the {@link HandlerMethod} and throw a
	 * {@link ConstraintViolationException} in case of violations.
	 * @param handlerMethod the handler method to validate
	 * @param arguments the input argument values
	 */
	public void validate(HandlerMethod handlerMethod, Object[] arguments) {
		Set<ConstraintViolation<Object>> result =
				this.validator.forExecutables().validateParameters(
						handlerMethod.getBean(), handlerMethod.getMethod(), arguments,
						determineValidationGroups(handlerMethod));
		if (!result.isEmpty()) {
			throw new ConstraintViolationException(result);
		}
	}

	/**
	 * Determine the validation groups to apply to a handler method, specified
	 * through the {@link Validated} annotation on the method or on the class.
	 * @param method the method to check
	 * @return the applicable validation groups as a Class array
	 */
	private Class<?>[] determineValidationGroups(HandlerMethod method) {
		Validated annotation = findAnnotation(method, Validated.class);
		return (annotation != null ? annotation.value() : new Class<?>[0]);
	}

	@Nullable
	private static <A extends Annotation> A findAnnotation(HandlerMethod method, Class<A> annotationType) {
		A annotation = AnnotationUtils.findAnnotation(method.getMethod(), annotationType);
		if (annotation == null) {
			annotation = AnnotationUtils.findAnnotation(method.getBeanType(), annotationType);
		}
		return annotation;
	}

	/**
	 * Whether the given method requires standard bean validation. This is the
	 * case if the method or one of its parameters are annotated with
	 * {@link Valid} or {@link Validated}, or if any method parameter is declared
	 * with a {@link Constraint constraint}, or the method parameter type is
	 * itself {@link BeanDescriptor#isBeanConstrained() constrained}.
	 * @param method the handler method to check
	 * @return {@code true} if validation is required, {@code false} otherwise
	 */
	public boolean requiresValidation(HandlerMethod method) {
		if (findAnnotation(method, Validated.class) != null || findAnnotation(method, Valid.class) != null) {
			return true;
		}
		for (MethodParameter parameter : method.getMethodParameters()) {
			for (Annotation annotation : parameter.getParameterAnnotations()) {
				MergedAnnotations merged = MergedAnnotations.from(annotation);
				if (merged.isPresent(Valid.class) || merged.isPresent(Constraint.class) || merged.isPresent(Validated.class)) {
					return true;
				}
			}
			Class<?> paramType = parameter.nestedIfOptional().getNestedParameterType();
			if (this.validator.getConstraintsForClass(paramType).isBeanConstrained()) {
				return true;
			}
		}
		return false;
	}


	/**
	 * Factory method for {@link HandlerMethodValidationHelper} if a
	 * {@link Validator} can be found.
	 * @param context the context to look up a {@code Validator} bean from
	 * @return the helper instance, or {@code null
	 */
	@Nullable
	public static HandlerMethodValidationHelper createIfValidatorAvailable(ApplicationContext context) {
		Validator validator = context.getBeanProvider(Validator.class).getIfAvailable();
		return (validator != null ? new HandlerMethodValidationHelper(validator) : null);
	}

}
