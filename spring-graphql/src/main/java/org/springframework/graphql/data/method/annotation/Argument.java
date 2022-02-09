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

import org.springframework.core.annotation.AliasFor;

/**
 * Annotation to bind a method parameter to a GraphQL input
 * {@link graphql.schema.DataFetchingEnvironment#getArgument(String) argument}.
 *
 * <p>If the method parameter is {@link java.util.Map Map&lt;String, Object&gt;}
 * and a parameter name is not specified, then the map parameter is populated
 * via {@link graphql.schema.DataFetchingEnvironment#getArguments()}.
 *
 * <p>The target method parameter can be a higher-level, typed Object of any
 * type. It is created and initialized from the named field argument value(s),
 * either matching them to single data constructor parameters, or using the
 * default constructor and then matching keys onto Object properties through
 * a {@link org.springframework.validation.DataBinder}.
 *
 * <p>Note that this annotation has neither a "required" flag nor the option to
 * specify a default value, both of which can be specified at the GraphQL schema
 * level and are enforced by the GraphQL Java engine.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see Arguments
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Argument {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the input argument to bind to.
	 */
	@AliasFor("value")
	String name() default "";

}
