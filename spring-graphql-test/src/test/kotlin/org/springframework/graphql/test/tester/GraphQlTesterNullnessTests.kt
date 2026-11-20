/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester

import org.junit.jupiter.api.Test

/**
 * Kotlin nullness tests for [GraphQlTester]
 * @author Brian Clozel
 */
class GraphQlTesterNullnessTests : GraphQlTesterTestSupport() {

    @Test
    fun valueIsEmptyList() {
        val document = "{me {name, friends}}"
        getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}")

        val response = graphQlTester().document(document).execute()
        response.path("me.friends").hasValue().entityList<MovieCharacter?>().hasSize(0)
    }

    @Test
    fun valueIsListWithNullEntry() {
        val document = "{me {name, friends}}"
        getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[null]}}")

        val response = graphQlTester().document(document).execute()
        response.path("me.friends").hasValue().entityList<MovieCharacter?>().contains(null);
    }

}