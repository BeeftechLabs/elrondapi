package com.beeftechlabs.model.network

import kotlinx.serialization.Serializable

@Serializable
data class NetworkStatus(
    val currentRound: Long,
    val epochNumber: Long,
    val highestFinalNonce: Long,
    val nonce: Long,
    val nonceAtEpochStart: Long,
    val noncesPassedInCurrentEpoch: Long,
    val roundsAtEpochStart: Long,
    val roundsPassedInCurrentEpoch: Int,
    val roundsPerEpoch: Int
)
