/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import java.lang.annotation.Annotation;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;
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
	private static @Nullable AuthenticationPrincipal findMethodAnnotation(MethodParameter parameter) {
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

	@SuppressWarnings("NullAway") // https://github.com/uber/NullAway/issues/1290
	@Override
	public @Nullable Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		return getCurrentAuthentication(parameter)
				.mapNotNull((auth) -> resolvePrincipal(parameter, auth.getPrincipal()))
				.transform((argument) -> isPublisherOrMono(parameter) ? Mono.just(argument) : argument);
	}

	private static boolean isPublisherOrMono(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();
		return (Publisher.class.equals(type) || Mono.class.equals(type));
	}

	@SuppressWarnings("unchecked")
	private Mono<Authentication> getCurrentAuthentication(MethodParameter parameter) {
		Object value = PrincipalMethodArgumentResolver.resolveAuthentication(parameter);
		return (value instanceof Authentication auth) ? Mono.just(auth) : (Mono<Authentication>) value;
	}

	private @Nullable Object resolvePrincipal(MethodParameter parameter, @Nullable Object principal) {
		AuthenticationPrincipal annotation = findMethodAnnotation(parameter);
		if (annotation != null && StringUtils.hasLength(annotation.expression())) {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setRootObject(principal);
			context.setVariable("this", principal);
			context.setBeanResolver(this.beanResolver);
			Expression expression = this.parser.parseExpression(annotation.expression());
			principal = expression.getValue(context);
		}
		if (isInvalidType(parameter, principal)) {
			if (annotation != null && annotation.errorOnInvalidType()) {
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
		if (isPublisherOrMono(parameter)) {
			Class<?> genericType = parameter.nested().getNestedParameterType();
			if (genericType.equals(Object.class)) {
				return false;
			}
			typeToCheck = genericType;
		}
		return !ClassUtils.isAssignable(typeToCheck, principal.getClass());
	}

}
