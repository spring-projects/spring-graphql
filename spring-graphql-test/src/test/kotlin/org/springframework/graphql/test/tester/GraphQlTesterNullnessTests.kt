/*
 * Copyright 2026-present the original author or authors.
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