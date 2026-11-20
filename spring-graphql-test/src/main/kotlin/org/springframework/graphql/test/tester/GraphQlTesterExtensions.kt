/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester

import org.springframework.core.ParameterizedTypeReference

/**
 * Extension for [GraphQlTester.Path.entity] providing a `entity<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any> GraphQlTester.Path.entity(): GraphQlTester.Entity<T, *> =
    entity(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [GraphQlTester.Path.entityList] providing a `entityList<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any?> GraphQlTester.Path.entityList(): GraphQlTester.EntityList<T> =
    entityList(object : ParameterizedTypeReference<T>() {})