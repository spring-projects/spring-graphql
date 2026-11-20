/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client

import org.springframework.core.ParameterizedTypeReference
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Extension for [GraphQlClient.RetrieveSpec.toEntity] providing a `toEntity<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any> GraphQlClient.RetrieveSpec.toEntity(): Mono<T> =
    toEntity(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [GraphQlClient.RetrieveSpec.toEntityList] providing a `toEntityList<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any> GraphQlClient.RetrieveSpec.toEntityList(): Mono<List<T>> =
    toEntityList(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [GraphQlClient.RetrieveSyncSpec.toEntity] providing a `toEntity<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any> GraphQlClient.RetrieveSyncSpec.toEntity(): T? =
    toEntity(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [GraphQlClient.RetrieveSyncSpec.toEntityList] providing a `toEntityList<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any> GraphQlClient.RetrieveSyncSpec.toEntityList(): List<T> =
    toEntityList(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [GraphQlClient.RetrieveSubscriptionSpec.toEntity] providing a `toEntity<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any> GraphQlClient.RetrieveSubscriptionSpec.toEntity(): Flux<T> =
    toEntity(object : ParameterizedTypeReference<T>() {})

/**
 * Extension for [GraphQlClient.RetrieveSubscriptionSpec.toEntityList] providing a `toEntityList<Book>()` variant
 * leveraging Kotlin reified type parameters. This extension is not subject to type
 * erasure and retains actual generic type arguments.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
inline fun <reified T : Any> GraphQlClient.RetrieveSubscriptionSpec.toEntityList(): Flux<List<T>> =
    toEntityList(object : ParameterizedTypeReference<T>() {})
