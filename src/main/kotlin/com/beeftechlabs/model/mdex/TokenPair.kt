package com.beeftechlabs.model.mdex

import com.beeftechlabs.model.token.TokenProperties
import kotlinx.serialization.Serializable

@Serializable
data class TokenPair(
    val first: TokenProperties,
    val second: TokenProperties,
    val address: String
)
