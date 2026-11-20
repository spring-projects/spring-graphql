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

/**
 * {@code @SubscriptionMapping} is a <em>composed annotation</em> that acts as a
 * shortcut for {@link SchemaMapping @SchemaMapping} with
 * {@code typeName="Subscription"}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SchemaMapping(typeName = "Subscription")
public @interface SubscriptionMapping {

	/**
	 * Alias for {@link SchemaMapping#field()}.
	 */
	@AliasFor(annotation = SchemaMapping.class, attribute = "field")
	String name() default "";

	/**
	 * Alias for {@link SchemaMapping#field()}.
	 */
	@AliasFor(annotation = SchemaMapping.class, attribute = "field")
	String value() default "";

}
