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

import graphql.schema.DataFetchingEnvironment;
import org.reactivestreams.Publisher;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;

/**
 * Resolver to obtain {@link Authentication#getPrincipal()} from Spring Security context via
 * {@link SecurityContext#getAuthentication()} for parameters annotated with {@link AuthenticationPrincipal}.
 *
 * <p>The resolver checks both ThreadLocal context via {@link SecurityContextHolder}
 * for Spring MVC applications, and {@link ReactiveSecurityContextHolder} for
 * Spring WebFlux applications.
 *
 * @author Rob Winch
 * @since 1.0.0
 */
public class AuthenticationPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

	private ExpressionParser parser = new SpelExpressionParser();

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
		return findMethodAnnotation(AuthenticationPrincipal.class, parameter) != null;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		return getCurrentAuthentication().map(Authentication::getPrincipal)
				.map((principal) -> resolvePrincipal(parameter, principal))
				.transform((argument) -> {
					Class<?> parameterType = parameter.getParameterType();
					boolean isParameterPublisher = Publisher.class.isAssignableFrom(parameterType);
					return isParameterPublisher ? Mono.just(argument) : argument;
				});
	}

	private Mono<Authentication> getCurrentAuthentication() {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		return Mono.justOrEmpty(securityContext.getAuthentication())
				.switchIfEmpty(ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication));
	}

	private Object resolvePrincipal(MethodParameter parameter, Object principal) {
		AuthenticationPrincipal annotation = findMethodAnnotation(AuthenticationPrincipal.class, parameter);
		String expressionToParse = annotation.expression();
		if (StringUtils.hasLength(expressionToParse)) {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setRootObject(principal);
			context.setVariable("this", principal);
			context.setBeanResolver(this.beanResolver);
			Expression expression = this.parser.parseExpression(expressionToParse);
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

	private boolean isInvalidType(MethodParameter parameter, Object principal) {
		if (principal == null) {
			return false;
		}
		Class<?> typeToCheck = parameter.getParameterType();
		boolean isParameterPublisher = Publisher.class.isAssignableFrom(parameter.getParameterType());
		if (isParameterPublisher) {
			ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
			Class<?> genericType = resolvableType.resolveGeneric(0);
			if (genericType == null) {
				return false;
			}
			typeToCheck = genericType;
		}
		return !ClassUtils.isAssignable(typeToCheck, principal.getClass());
	}

	/**
	 * Obtains the specified {@link Annotation} on the specified {@link MethodParameter}.
	 * @param annotationClass the class of the {@link Annotation} to find on the
	 * {@link MethodParameter}
	 * @param parameter the {@link MethodParameter} to search for an {@link Annotation}
	 * @return the {@link Annotation} that was found or null.
	 */
	private <T extends Annotation> T findMethodAnnotation(Class<T> annotationClass, MethodParameter parameter) {
		T annotation = parameter.getParameterAnnotation(annotationClass);
		if (annotation != null) {
			return annotation;
		}
		Annotation[] annotationsToSearch = parameter.getParameterAnnotations();
		for (Annotation toSearch : annotationsToSearch) {
			annotation = AnnotationUtils.findAnnotation(toSearch.annotationType(), annotationClass);
			if (annotation != null) {
				return annotation;
			}
		}
		return null;
	}
}
