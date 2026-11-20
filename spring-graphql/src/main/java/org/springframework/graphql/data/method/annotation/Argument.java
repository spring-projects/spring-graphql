/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.validation.BindException;

/**
 * Annotation to bind a named GraphQL
 * {@link graphql.schema.DataFetchingEnvironment#getArgument(String) argument}
 * onto a method parameter.
 *
 * <p>Binding is performed by mapping argument values to a primary data
 * constructor of the expected method parameter type, or by using a default
 * constructor to create it and then map values to its properties. This is
 * applied recursively, using all nested values and creating nested target
 * objects.
 *
 * <p>If binding fails, a {@link BindException} is raised with binding issues
 * accumulated as {@link BindException#getFieldErrors() field errors} where the
 * {@code field} of each error is the argument path where the issue occurred.
 *
 * <p>If the method parameter is {@link java.util.Map Map&lt;String, Object&gt;}
 * then the raw argument value for the named argument is used. For access to the
 * full {@link graphql.schema.DataFetchingEnvironment#getArguments() arguments}
 * map, use {@link Arguments @Arguments} instead.
 *
 * <p>This annotation has neither a "required" flag nor the option to specify a
 * default value, both of which can be specified at the GraphQL schema level
 * and are enforced by the GraphQL Java engine.
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
