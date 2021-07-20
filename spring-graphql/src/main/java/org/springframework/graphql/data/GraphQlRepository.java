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
