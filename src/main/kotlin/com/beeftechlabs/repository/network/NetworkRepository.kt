package com.beeftechlabs.repository.network

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.model.network.NetworkConfig
import com.beeftechlabs.model.network.NetworkStatus
import com.beeftechlabs.service.GatewayService

object NetworkRepository {

    suspend fun getNetworkConfig(): NetworkConfig =
        with(GatewayService.get<NetworkConfigResponse>("network/config").data.config) {
            NetworkConfig(
                chainId,
                denomination,
                gasPerDataByte,
                gasPriceModifier,
                latestTagSoftwareVersion,
                maxGasPerTransaction,
                minGasLimit,
                minGasPrice,
                minTransactionVersion,
                numMetachainNodes,
                numNodesInShard,
                numShardsWithoutMeta,
                rewardsTopUpGradientPoint,
                roundDuration,
                roundsPerEpoch,
                metaConsensusGroupSize,
                shardConsensusGroupSize,
                startTime,
                topUpFactor
            )
        }

    suspend fun getNetworkStatus(): NetworkStatus =
        with(GatewayService.get<NetworkStatusResponse>("network/status/4294967295").data.status) {
            NetworkStatus(
                currentRound,
                epochNumber,
                highestFinalNonce,
                nonce,
                nonceAtEpochStart,
                noncesPassedInCurrentEpoch,
                roundsAtEpochStart,
                roundsPassedInCurrentEpoch,
                roundsPerEpoch
            )
        }
}

suspend fun NetworkConfig.Companion.cached() = withCache(CacheType.NetworkConfig) { NetworkRepository.getNetworkConfig() }

suspend fun NetworkStatus.Companion.cached() = withCache(CacheType.NetworkStatus) { NetworkRepository.getNetworkStatus() }