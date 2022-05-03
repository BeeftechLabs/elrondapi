package com.beeftechlabs.zoidpay.model

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class Stake(
    val pool: String,
    val stakedAmount: Value,
    val timestamp: Long,
    val months: Int,
    val isPool: Boolean
)