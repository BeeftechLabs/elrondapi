package com.beeftechlabs.repository

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.address.AddressDelegation
import com.beeftechlabs.model.address.UndelegatedValue
import com.beeftechlabs.model.core.StakingProvider
import com.beeftechlabs.model.network.NetworkConfig
import com.beeftechlabs.model.network.NetworkStatus
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.repository.network.cached
import com.beeftechlabs.service.SCService
import com.beeftechlabs.util.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

object StakingRepository {

    private val elrondConfig by lazy { config.elrond!! }

    suspend fun getDelegations(address: String): List<AddressDelegation> = coroutineScope {
        val providers = async { StakingProviders.cached().value }
        val delegationsDeferred = async {
            withCache(CacheType.AddressDelegations, address) {
                ElasticRepository.getDelegations(address)
            }
        }
        val networkConfig = async { NetworkConfig.cached() }
        val networkStatus = async { NetworkStatus.cached() }

        val delegations = delegationsDeferred.await()

        val undelegations =
            withCache(CacheType.AddressUndelegations, address) {
                delegations.map {
                    it.stakingProvider.address to async {
                        getAddressUndelegatedListForContract(
                            address,
                            it.stakingProvider.address,
                            networkConfig.await(),
                            networkStatus.await()
                        )
                    }
                }.associate { it.first to it.second.await() }
            }

        delegations.map { delegation ->
            delegation.copy(
                stakingProvider = providers.await().find { it.address == delegation.stakingProvider.address }
                    ?: delegation.stakingProvider,
                undelegatedList = undelegations[delegation.stakingProvider.address] ?: emptyList()
            )
        }
    }

    suspend fun getDelegationProviders(): StakingProviders {
        val providerAddresses =
            SCService.vmQuery(elrondConfig.delegationManager, "getAllContractAddresses").map {
                Address(it.fromBase64ToHexString()).erd
            }
        return StakingProviders(
            withContext(Dispatchers.IO) {
                providerAddresses
                    .chunked(NUM_PARALLEL_PROVIDER_FETCH)
                    .map { chunks ->
                        chunks.map { async { getProviderDetails(it) } }.awaitAll()
                    }
                    .flatten().mapNotNull { it }
            }
        )
    }

    private suspend fun getProviderDetails(address: String): StakingProvider? = coroutineScope {
        val provider = async { getDelegationContractConfig(address) }
        val metadata = async { getDelegationContractMetadata(address) }
        provider.await()?.copy(metadata = metadata.await())
    }

    private suspend fun getDelegationContractConfig(address: String): StakingProvider? {
        val contractConfig = SCService.vmQuery(address, "getContractConfig")
        if (contractConfig.size < 3) {
            return null
        }

        val owner = Address(contractConfig[0].fromBase64ToHexString()).erd
        val serviceFee = if (contractConfig[1].isNotEmpty()) {
            BigDecimal.fromBigInteger(contractConfig[1].fromBase64ToHexString().toBigInteger(16))
                .divide(BigDecimal.fromInt(10000))
                .toDouble()
        } else {
            null
        }
        val delegationCap = if (contractConfig[2].isNotEmpty()) {
            contractConfig[2].fromBase64ToHexString().denominatedBigDecimal().toLong()
        } else {
            null
        }

        return StakingProvider(
            address = address,
            owner = owner,
            serviceFee = serviceFee,
            delegationCap = delegationCap
        )
    }

    private suspend fun getDelegationContractMetadata(address: String): StakingProvider.Metadata? {
        val metadata = SCService.vmQuery(address, "getMetaData")
        if (metadata.size < 3) {
            return null
        }

        return StakingProvider.Metadata(
            name = metadata.getOrNull(0)?.fromBase64String(),
            website = metadata.getOrNull(1)?.fromBase64String(),
            keybaseIdentity = metadata.getOrNull(2)?.fromBase64String()
        )
    }

    private suspend fun getAddressUndelegatedListForContract(
        address: String,
        contract: String,
        networkConfig: NetworkConfig,
        networkStatus: NetworkStatus
    ): List<UndelegatedValue> {
        val response = SCService.vmQuery(contract, "getUserUnDelegatedList", listOf(Address(address).hex))

        val roundsRemaining by lazy { networkConfig.roundsPerEpoch - networkStatus.roundsPassedInCurrentEpoch }

        return response.zipWithNext().map { (valueBase64, epochsRemainingBase64) ->
            val roundsUntilComplete =
                ((epochsRemainingBase64.vmQueryToLong() - 1) * networkConfig.roundsPerEpoch) + roundsRemaining
            val timeLeft = roundsUntilComplete * networkConfig.roundDuration

            UndelegatedValue(
                Value.extractHex(valueBase64.fromBase64ToHexString(), "EGLD"),
                timeLeft.coerceAtLeast(0)
            )
        }
    }

    private const val NUM_PARALLEL_PROVIDER_FETCH = 100
}

@Serializable
data class StakingProviders(
    val value: List<StakingProvider>
) {
    companion object {
        suspend fun cached() = withCache(CacheType.StakingProviders) { StakingRepository.getDelegationProviders() }
    }
}