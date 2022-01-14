package com.beeftechlabs.repository

import com.beeftechlabs.cache.getFromStore
import com.beeftechlabs.cache.getFromStoreStrictly
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.address.AddressDelegation
import com.beeftechlabs.model.core.StakingProvider
import com.beeftechlabs.util.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.*

object StakingRepository {

    private val elrondConfig by lazy { config.elrond!! }

    suspend fun getDelegations(address: String): List<AddressDelegation> {
        val providers = getFromStore<StakingProviders>().value
        val delegations = ElasticRepository.getDelegations(address)
        return delegations.map { delegation ->
            delegation.copy(stakingProvider = providers.find { it.address == delegation.stakingProvider.address }
                ?: delegation.stakingProvider)
        }
    }

    suspend fun getDelegationProviders(): StakingProviders {
        return getFromStoreStrictly<StakingProviders>() ?: run {
            val providerAddresses =
                SCRepository.vmQuery(elrondConfig.delegationManager, "getAllContractAddresses").map {
                    Address(it.fromBase64ToHexString()).erd
                }
            StakingProviders(
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
    }

    private suspend fun getProviderDetails(address: String): StakingProvider? = coroutineScope {
        val provider = async { getDelegationContractConfig(address) }
        val metadata = async { getDelegationContractMetadata(address) }
        provider.await()?.copy(metadata = metadata.await())
    }

    private suspend fun getDelegationContractConfig(address: String): StakingProvider? {
        val contractConfig = SCRepository.vmQuery(address, "getContractConfig")
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
        val metadata = SCRepository.vmQuery(address, "getMetaData")
        if (metadata.size < 3) {
            return null
        }

        return StakingProvider.Metadata(
            name = metadata.getOrNull(0)?.fromBase64String(),
            website = metadata.getOrNull(1)?.fromBase64String(),
            keybaseIdentity = metadata.getOrNull(2)?.fromBase64String()
        )
    }

    private const val NUM_PARALLEL_PROVIDER_FETCH = 100
}

data class StakingProviders(
    val value: List<StakingProvider>
)