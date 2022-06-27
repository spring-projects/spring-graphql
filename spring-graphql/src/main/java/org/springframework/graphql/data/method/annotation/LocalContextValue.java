/*
 * Copyright 2002-2021 the original author or authors.
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

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.annotation.AliasFor;


/**
 * Annotation to bind a method parameter to an attribute from the
 * {@link DataFetchingEnvironment#getLocalContext() local} {@code GraphQLContext}.
 *
 * <p>To bind to an attribute from the main context instead, see
 * {@link ContextValue @ContextValue}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see ContextValue
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LocalContextValue {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the value to bind to.
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Whether the value is required.
	 * <p>Defaults to "true", leading to an exception thrown if the value is
	 * missing. Switch to "false" if you prefer {@code null} if the value is
	 * not present, or use {@link java.util.Optional}.
	 */
	boolean required() default true;

}
