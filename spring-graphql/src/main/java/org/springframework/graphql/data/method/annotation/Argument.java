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
 * <p>This annotation  does not specify whether the input argument is required
 * and if it should use a default value: this should be done at the schema
 * in order to be enforced by the GraphQL engine itself.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
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
