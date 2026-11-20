/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference

/**
 * Tests for [GraphQlTester] Kotlin extensions.
 *
 * @author Brian Clozel
 */
class GraphQlTesterExtensionsTests {

    private val testerPath = mockk<GraphQlTester.Path>(relaxed = true)

    @Test
    fun `Path#entity with reified type parameters`() {
        testerPath.entity<Map<String, Book>>()
        verify { testerPath.entity(object : ParameterizedTypeReference<Map<String, Book>>() {}) }
    }

    @Test
    fun `Path#entityList with reified type parameters`() {
        testerPath.entityList<Map<String, Book>>()
        verify { testerPath.entityList(object : ParameterizedTypeReference<Map<String, Book>>() {}) }
    }

    private class Book {}

}