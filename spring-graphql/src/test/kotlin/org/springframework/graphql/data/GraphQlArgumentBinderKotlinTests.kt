/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.graphql.data

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.json.JsonMapper
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.ResolvableType
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.lang.Nullable

class GraphQlArgumentBinderKotlinTests {

    private val mapper = JsonMapper.builder().build()

    private val binder = GraphQlArgumentBinder(DefaultFormattingConversionService())

    @Test
    fun bindValueClass() {
        val targetType = ResolvableType.forClass(MyDataClassWithValueClass::class.java)
        val result = bind(binder, "{\"first\": \"firstValue\", \"second\": \"secondValue\"}", targetType) as MyDataClassWithValueClass
        assertThat(result.first).isEqualTo("firstValue")
        assertThat(result.second).isEqualTo(MyValueClass("secondValue"))
    }

    @Test
    fun bindValueClassWithDefaultValue() {
        val targetType = ResolvableType.forClass(MyDataClassWithDefaultValueClass::class.java)
        val result = bind(binder, "{\"first\": \"firstValue\"}", targetType) as MyDataClassWithDefaultValueClass
        assertThat(result.first).isEqualTo("firstValue")

    }

    @Nullable
    @Throws(Exception::class)
    private fun bind(binder: GraphQlArgumentBinder, json: String, targetType: ResolvableType): Any? {
        val typeRef: TypeReference<Map<String, Any>> = object : TypeReference<Map<String, Any>>() {}
        val map = this.mapper.readValue("{\"key\":$json}", typeRef)
        val environment: DataFetchingEnvironment =
            DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .arguments(map)
                .build()
        return binder.bind(environment, "key", targetType)
    }

    data class MyDataClassWithValueClass(val first: String, val second: MyValueClass)

    data class MyDataClassWithDefaultValueClass(val first: String, val second: MyValueClass = MyValueClass("secondValue"))

    @JvmInline
    value class MyValueClass(val value: String) {

    }

}
