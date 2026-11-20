/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client

import org.springframework.core.ParameterizedTypeReference

/**
 * Extension for [ClientResponseField.toEntity] providing a `toEntity<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.1
 */
inline fun <reified T : Any> ClientResponseField.toEntity(): T? =
    toEntity(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [ClientResponseField.toEntityList] providing a `toEntityList<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.1
 */
inline fun <reified T : Any> ClientResponseField.toEntityList(): List<T> =
    toEntityList(object : ParameterizedTypeReference<T>() {})
