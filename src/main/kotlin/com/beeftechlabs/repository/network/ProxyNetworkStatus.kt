package com.beeftechlabs.repository.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkStatusResponse(
    val data: NetworkStatusWrapper
)

@Serializable
data class NetworkStatusWrapper(
    val status: ProxyNetworkStatus
)

@Serializable
data class ProxyNetworkStatus(
    @SerialName("erd_current_round") val currentRound: Long,
    @SerialName("erd_epoch_number") val epochNumber: Long,
    @SerialName("erd_highest_final_nonce") val highestFinalNonce: Long,
    @SerialName("erd_nonce") val nonce: Long,
    @SerialName("erd_nonce_at_epoch_start") val nonceAtEpochStart: Long,
    @SerialName("erd_nonces_passed_in_current_epoch") val noncesPassedInCurrentEpoch: Long,
    @SerialName("erd_round_at_epoch_start") val roundsAtEpochStart: Long,
    @SerialName("erd_rounds_passed_in_current_epoch") val roundsPassedInCurrentEpoch: Int,
    @SerialName("erd_rounds_per_epoch") val roundsPerEpoch: Int
)
