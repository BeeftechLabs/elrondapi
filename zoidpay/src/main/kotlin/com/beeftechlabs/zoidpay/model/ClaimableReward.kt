package com.beeftechlabs.zoidpay.model

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class ClaimableReward(
    val pool: String,
    val timestamp: Long,
    val reward: Value
)
