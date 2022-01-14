package com.beeftechlabs.model.core

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class Delegator(
    val address: String,
    val value: Value
)
