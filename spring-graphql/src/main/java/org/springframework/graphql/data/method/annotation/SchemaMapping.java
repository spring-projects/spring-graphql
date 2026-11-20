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
 * Annotation to express the mapping of a handler method to a GraphQL type and
 * field pair.
 *
 * <p>You can use this annotation both at the method and at the class level, in
 * which case all handlers methods inherit the class-level typeName by default
 * unless overridden at the method level.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SchemaMapping {

	/**
	 * Customize the name of the GraphQL field to bind to.
	 * <p>By default, if not specified, this is initialized from the method name.
	 */
	@AliasFor("value")
	String field() default "";

	/**
	 * Effectively an alias for {@link #field()}.
	 */
	@AliasFor("field")
	String value() default "";

	/**
	 * Customizes the name of the source/parent type for the GraphQL field.
	 * <p>By default, if not specified, it is derived from the class name of a
	 * {@link DataFetchingEnvironment#getSource() source} argument injected into
	 * the handler method.
	 * <p>This attributed is supported at the class level and at the method level!
	 * When used on both levels, the one on the method level overrides the one
	 * at the class level.
	 */
	String typeName() default "";

}
