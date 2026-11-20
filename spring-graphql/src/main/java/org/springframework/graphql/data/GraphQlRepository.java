/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Repository;

/**
 * Specialization of the {@link Repository} stereotype that marks a repository
 * as intended for use in a GraphQL application for data fetching.
 *
 * <p>A Spring Data repository that is an
 * {@link org.springframework.data.querydsl.QuerydslPredicateExecutor} or
 * {@link org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor} is
 * eligible for auto-binding to queries whose return type matches the repository
 * domain type name.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repository
public @interface GraphQlRepository {

	/**
	 * Use this to customize the name of the GraphQL type that matches to the
	 * repository domain type.
	 * <p>By default, if this is not specified, then the simple name of the
	 * repository domain type is used to match to the GraphQL schema type.
	 */
	String typeName() default "";

}
