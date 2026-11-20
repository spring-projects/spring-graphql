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

/**
 * Analogous to {@link Argument} but binding with the full
 * {@link DataFetchingEnvironment#getArgument(String) arguments} map.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Arguments {

}
