package com.beeftechlabs.model.network

import kotlinx.serialization.Serializable

@Serializable
data class NetworkConfig(
    val chainId: String,
    val denomination: Int,
    val gasPerDataByte: Int,
    val gasPriceModifier: Double,
    val latestTagSoftwareVersion: String,
    val maxGasPerTransaction: Long,
    val minGasLimit: Long,
    val minGasPrice: Long,
    val minTransactionVersion: Int,
    val numMetachainNodes: Int,
    val numNodesInShard: Int,
    val numShardsWithoutMeta: Int,
    val rewardsTopUpGradientPoint: String,
    val roundDuration: Int,
    val roundsPerEpoch: Int,
    val metaConsensusGroupSize: Int,
    val shardConsensusGroupSize: Int,
    val startTime: Long,
    var topUpFactor: Double
)
