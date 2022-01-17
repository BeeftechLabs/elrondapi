package com.beeftechlabs.repository.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkConfigResponse(
    val data: NetworkConfigWrapper
)

@Serializable
data class NetworkConfigWrapper(
    val config: ProxyNetworkConfig
)

@Serializable
data class ProxyNetworkConfig(
    @SerialName("erd_chain_id") val chainId: String,
    @SerialName("erd_denomination") val denomination: Int,
    @SerialName("erd_gas_per_data_byte") val gasPerDataByte: Int,
    @SerialName("erd_gas_price_modifier") val gasPriceModifier: Double,
    @SerialName("erd_latest_tag_software_version") val latestTagSoftwareVersion: String,
    @SerialName("erd_max_gas_per_transaction") val maxGasPerTransaction: Long,
    @SerialName("erd_min_gas_limit") val minGasLimit: Long,
    @SerialName("erd_min_gas_price") val minGasPrice: Long,
    @SerialName("erd_min_transaction_version") val minTransactionVersion: Int,
    @SerialName("erd_num_metachain_nodes") val numMetachainNodes: Int,
    @SerialName("erd_num_nodes_in_shard") val numNodesInShard: Int,
    @SerialName("erd_num_shards_without_meta") val numShardsWithoutMeta: Int,
    @SerialName("erd_rewards_top_up_gradient_point") val rewardsTopUpGradientPoint: String,
    @SerialName("erd_round_duration") val roundDuration: Int,
    @SerialName("erd_rounds_per_epoch") val roundsPerEpoch: Int,
    @SerialName("erd_meta_consensus_group_size") val metaConsensusGroupSize: Int,
    @SerialName("erd_shard_consensus_group_size") val shardConsensusGroupSize: Int,
    @SerialName("erd_start_time") val startTime: Long,
    @SerialName("erd_top_up_factor") var topUpFactor: Double
)
