package com.beeftechlabs.model.address

import com.beeftechlabs.model.core.StakingProvider
import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class AddressDelegation(
    val stakingProvider: StakingProvider,
    val value: Value,
    val claimable: Value,
    val totalRewards: Value,
    val undelegatedList: List<UndelegatedValue> = emptyList()
)

@Serializable
data class UndelegatedValue(
    val value: Value,
    val millisRemaining: Long
)
