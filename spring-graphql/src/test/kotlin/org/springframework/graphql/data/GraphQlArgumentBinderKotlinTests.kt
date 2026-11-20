/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
import org.springframework.graphql.data.GraphQlArgumentBinder.Options

/**
 * Tests for [GraphQlArgumentBinder]
 */
class GraphQlArgumentBinderKotlinTests {

    private val mapper = JsonMapper.builder().build()

    private val binder = GraphQlArgumentBinder(Options.create().conversionService(DefaultFormattingConversionService()))

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
