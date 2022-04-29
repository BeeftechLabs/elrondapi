package com.beeftechlabs.model.mdex

import com.beeftechlabs.model.token.TokenProperties
import kotlinx.serialization.Serializable

@Serializable
data class TokenPair(
    val first: TokenProperties,
    val second: TokenProperties,
    val address: String,
    val totalFeePercent: Double? = null,
    val specialFeePercent: Double? = null,
    val state: TokenPairState? = null,
    val lpToken: TokenProperties? = null,
    val lpTokenTotalSupply: String? = null,
)

enum class TokenPairState { Inactive, Active, ActiveNoSwaps }