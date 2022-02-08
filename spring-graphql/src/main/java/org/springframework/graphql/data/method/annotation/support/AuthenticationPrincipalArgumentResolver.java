/*
 * Copyright 2002-2022 the original author or authors.
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

import graphql.schema.DataFetchingEnvironment;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Resolver to obtain {@link Authentication#getPrincipal()} from Spring Security
 * context via {@link SecurityContext#getAuthentication()} for parameters
 * annotated with {@link AuthenticationPrincipal}.
 *
 * <p>The resolver checks both ThreadLocal context via {@link SecurityContextHolder}
 * for Spring MVC applications, and {@link ReactiveSecurityContextHolder} for
 * Spring WebFlux applications.
 *
 * @author Rob Winch
 * @since 1.0.0
 */
public class AuthenticationPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

	private final ExpressionParser parser = new SpelExpressionParser();

	private final BeanResolver beanResolver;


	/**
	 * Creates a new instance.
	 * @param beanResolver the {@link BeanResolver} used for resolving beans in SpEL expressions. Cannot be null.
	 */
	public AuthenticationPrincipalArgumentResolver(BeanResolver beanResolver) {
		Assert.notNull(beanResolver, "BeanResolver is required");
		this.beanResolver = beanResolver;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return findMethodAnnotation(parameter) != null;
	}

	/**
	 * Obtains the {@link AuthenticationPrincipal} annotation which can be
	 * directly on the {@link MethodParameter} or on a custom annotation that
	 * is meta-annotated with it.
	 */
	@Nullable
	private static AuthenticationPrincipal findMethodAnnotation(MethodParameter parameter) {
		AuthenticationPrincipal annotation = parameter.getParameterAnnotation(AuthenticationPrincipal.class);
		if (annotation != null) {
			return annotation;
		}
		Annotation[] annotationsToSearch = parameter.getParameterAnnotations();
		for (Annotation toSearch : annotationsToSearch) {
			annotation = AnnotationUtils.findAnnotation(toSearch.annotationType(), AuthenticationPrincipal.class);
			if (annotation != null) {
				return annotation;
			}
		}
		return null;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		return getCurrentAuthentication()
				.flatMap(auth -> Mono.justOrEmpty(resolvePrincipal(parameter, auth.getPrincipal())))
				.transform((argument) -> isParameterMonoAssignable(parameter) ? Mono.just(argument) : argument);
	}

	private static boolean isParameterMonoAssignable(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();
		return (Publisher.class.equals(type) || Mono.class.equals(type));
	}

	private Mono<Authentication> getCurrentAuthentication() {
		return Mono.justOrEmpty(SecurityContextHolder.getContext().getAuthentication())
				.switchIfEmpty(ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication));
	}

	@Nullable
	private Object resolvePrincipal(MethodParameter parameter, Object principal) {
		AuthenticationPrincipal annotation = findMethodAnnotation(parameter);
		String expressionValue = annotation.expression();
		if (StringUtils.hasLength(expressionValue)) {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setRootObject(principal);
			context.setVariable("this", principal);
			context.setBeanResolver(this.beanResolver);
			Expression expression = this.parser.parseExpression(expressionValue);
			principal = expression.getValue(context);
		}
		if (isInvalidType(parameter, principal)) {
			if (annotation.errorOnInvalidType()) {
				throw new ClassCastException(principal + " is not assignable to " + parameter.getParameterType());
			}
			return null;
		}
		return principal;
	}

	private boolean isInvalidType(MethodParameter parameter, @Nullable Object principal) {
		if (principal == null) {
			return false;
		}
		Class<?> typeToCheck = parameter.getParameterType();
		if (isParameterMonoAssignable(parameter)) {
			Class<?> genericType = parameter.nested().getNestedParameterType();
			if (genericType.equals(Object.class)) {
				return false;
			}
			typeToCheck = genericType;
		}
		return !ClassUtils.isAssignable(typeToCheck, principal.getClass());
	}

}
