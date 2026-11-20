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
