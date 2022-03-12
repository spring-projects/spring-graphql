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

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * Strategy for validating a {@link HandlerMethod} input before invocation, based on JSR-303.
 * This is called after all {@link HandlerMethodArgumentResolver} have been involved.
 *
 * @author Brian Clozel
 */
class HandlerMethodInputValidator {

	private final Validator validator;

	/**
	 * Create the input validator backed by a JSR-303 Validator instance.
	 */
	public HandlerMethodInputValidator(Validator validator) {
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
	 * Create the input validator backed by a default
	 * {@link Validation#buildDefaultValidatorFactory() factory instance}.
	 */
	public HandlerMethodInputValidator() {
		this(Validation.buildDefaultValidatorFactory().getValidator());
	}

	/**
	 * Validate the {@link HandlerMethod} input before invocation, throwing
	 * an {@link ConstraintViolationException} if validation fails.
	 *
	 * @param handlerMethod the handler method for the current request
	 * @param arguments the resolved arguments for the method invocation
	 */
	public void validate(HandlerMethod handlerMethod, Object[] arguments) {
		Class<?>[] validationGroups = determineValidationGroups(handlerMethod);
		Set<ConstraintViolation<Object>> result = this.validator.forExecutables()
				.validateParameters(handlerMethod.getBean(), handlerMethod.getMethod(), arguments, validationGroups);
		if (!result.isEmpty()) {
			throw new ConstraintViolationException(result);
		}
	}

	/**
	 * Determine the validation groups to validate against for the given handler method.
	 * <p>Default are the validation groups as specified in the {@link Validated} annotation
	 * on the containing target class of the method.
	 * @param method the current HandlerMethod
	 * @return the applicable validation groups as a Class array
	 */
	private Class<?>[] determineValidationGroups(HandlerMethod method) {
		Validated validatedAnn = AnnotationUtils.findAnnotation(method.getMethod(), Validated.class);
		if (validatedAnn == null) {
			validatedAnn = AnnotationUtils.findAnnotation(method.getBeanType(), Validated.class);
		}
		return (validatedAnn != null ? validatedAnn.value() : new Class<?>[0]);
	}
}
