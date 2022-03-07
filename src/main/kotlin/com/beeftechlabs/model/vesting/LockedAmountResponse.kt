package com.beeftechlabs.model.vesting

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class LockedAmountResponse(
    val lockedAmount: Value,
    val unlockTimestamp: Long
)
