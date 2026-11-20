/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference


/**
 * Tests for [GraphQlClient] Kotlin extensions.
 *
 * @author Brian Clozel
 */
class GraphQlClientExtensionsTests {

    private val retrieveSpec = mockk<GraphQlClient.RetrieveSpec>(relaxed = true)

    private val retrieveSyncSpec = mockk<GraphQlClient.RetrieveSyncSpec>(relaxed = true)

    private val retrieveSubscriptionSpec = mockk<GraphQlClient.RetrieveSubscriptionSpec>(relaxed = true)


    @Test
    fun `RetrieveSpec#toEntity with reified type parameters`() {
        retrieveSpec.toEntity<Map<String, Book>>()
        verify { retrieveSpec.toEntity(object : ParameterizedTypeReference<Map<String, Book>>() {}) }
    }

    @Test
    fun `RetrieveSpec#toEntityList with reified type parameters`() {
        retrieveSpec.toEntityList<Book>()
        verify { retrieveSpec.toEntityList(object : ParameterizedTypeReference<Book>() {}) }
    }

    @Test
    fun `RetrieveSyncSpec#toEntity with reified type parameters`() {
        retrieveSyncSpec.toEntity<Map<String, Book>>()
        verify { retrieveSyncSpec.toEntity(object : ParameterizedTypeReference<Map<String, Book>>() {}) }
    }

    @Test
    fun `RetrieveSyncSpec#toEntityList with reified type parameters`() {
        retrieveSyncSpec.toEntityList<Book>()
        verify { retrieveSyncSpec.toEntityList(object : ParameterizedTypeReference<Book>() {}) }
    }

    @Test
    fun `RetrieveSubscriptionSpec#toEntity with reified type parameters`() {
        retrieveSubscriptionSpec.toEntity<Map<String, Book>>()
        verify { retrieveSubscriptionSpec.toEntity(object : ParameterizedTypeReference<Map<String, Book>>() {}) }
    }

    @Test
    fun `RetrieveSubscriptionSpec#toEntityList with reified type parameters`() {
        retrieveSubscriptionSpec.toEntityList<Book>()
        verify { retrieveSubscriptionSpec.toEntityList(object : ParameterizedTypeReference<Book>() {}) }
    }


    private class Book

}
