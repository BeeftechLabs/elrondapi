package com.beeftechlabs.model.core

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class Unstaked(
    val value: Value,
    val epochsRemaining: Int
)
