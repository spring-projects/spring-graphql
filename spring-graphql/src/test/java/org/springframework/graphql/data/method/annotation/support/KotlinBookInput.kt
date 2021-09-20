package org.springframework.graphql.data.method.annotation.support;

import org.springframework.graphql.data.method.OptionalInput

data class KotlinBookInput(
    val name: String, val authorId: Long,
    val notes: OptionalInput<String?>
)
