/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.federation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * Annotation for mapping a handler method to a federated schema type.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EntityMapping {

	/**
	 * Customize the name of the entity to map to.
	 * <p>By default, if not specified, this is initialized from the method name,
	 * with the first letter changed to upper case via {@link Character#toUpperCase}.
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Effectively an alias for {@link #name()}.
	 */
	@AliasFor("name")
	String value() default "";

}
