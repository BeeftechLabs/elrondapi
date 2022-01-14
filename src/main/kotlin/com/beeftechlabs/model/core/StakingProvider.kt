package com.beeftechlabs.model.core

import kotlinx.serialization.Serializable

@Serializable
data class StakingProvider(
    val address: String = "",
    val owner: String = "",
    val serviceFee: Double? = null,
    val delegationCap: Long? = null,
    val metadata: Metadata? = null
) {
    @Serializable
    data class Metadata(
        val name: String? = null,
        val website: String? = null,
        val keybaseIdentity: String? = null
    )
}
