package org.springframework.graphql.data.method.annotation.support;

import java.util.Optional

data class KotlinBookInput(
    val name: String, val authorId: Long,
    val notes: Optional<String?>?
)
