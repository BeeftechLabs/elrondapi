package com.beeftechlabs.model.token

import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val value: Value,
    val properties: TokenProperties
)