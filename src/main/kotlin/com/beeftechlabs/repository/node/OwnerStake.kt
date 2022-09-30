package com.beeftechlabs.repository.node

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

@Serializable
data class OwnerStake(
    val address: String,
    val name: String?,
    val topUp: Value,
    val topUpPerNode: Value,
    val staked: Value,
    val numNodes: Int,
    val blsKeys: List<String>?,
    val nodeStartIdx: Int? = null,
    val nodeEndIdx: Int? = null
)
