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
 * Tests for [ClientResponseField] Kotlin extensions.
 *
 * @author Brian Clozel
 */
class GraphQlResponseFieldExtensionsTests {

    private val responseField = mockk<ClientResponseField>(relaxed = true)

    @Test
    fun `ClientResponseField#toEntity with reified type parameters`() {
        responseField.toEntity<Map<String, Book>>()
        verify { responseField.toEntity(object : ParameterizedTypeReference<Map<String, Book>>() {}) }
    }

    @Test
    fun `ClientResponseField#toEntityList with reified type parameters`() {
        responseField.toEntityList<Book>()
        verify { responseField.toEntityList(object : ParameterizedTypeReference<Book>() {}) }
    }

    private class Book

}
