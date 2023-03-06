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
package org.springframework.graphql.data.method.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Declares a method as a handler of exceptions raised while fetching data
 * for a field. When declared in an
 * {@link org.springframework.stereotype.Controller @Controller}, it applies to
 * {@code @SchemaMapping} methods of that controller only. When declared in an
 * {@link org.springframework.web.bind.annotation.ControllerAdvice @ControllerAdvice}
 * it applies across controllers.
 *
 * <p>You can also use annotated exception handler methods in
 * {@code @ControllerAdvice} beans to handle exceptions from non-controller
 * {@link graphql.schema.DataFetcher}s by obtaining
 * {@link org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer#getExceptionResolver()}
 * and registering it with
 * {@link org.springframework.graphql.execution.GraphQlSource.Builder#exceptionResolvers(List)
 * GraphQlSource.Builder}.
 *
 * <p>Supported return types are listed in the Spring for GraphQL reference documentation
 * in the section {@literal "Annotated Controllers"}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GraphQlExceptionHandler {

	/**
	 * Exceptions handled by the annotated method. If empty, defaults to
	 * exception types declared in the method signature.
	 */
	Class<? extends Throwable>[] value() default {};

}
