package com.beeftechlabs.repository.mdex

import com.beeftechlabs.cache.CacheType
import com.beeftechlabs.cache.withCache
import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.mdex.TokenPair
import com.beeftechlabs.model.mdex.TokenPairState
import com.beeftechlabs.repository.elastic.*
import com.beeftechlabs.repository.elastic.model.ElasticScResult
import com.beeftechlabs.repository.elastic.model.ElasticTransaction
import com.beeftechlabs.repository.token.AllTokens
import com.beeftechlabs.service.SCService
import com.beeftechlabs.util.fromBase64String
import com.beeftechlabs.util.fromHexString
import com.beeftechlabs.util.letAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

object MdexRepository {

    val elrondConfig by lazy { config.elrond!! }

    suspend fun getAllPairs(): List<TokenPair> = coroutineScope {
        val createTransactionsDeferred = async(Dispatchers.IO) {
            ElasticService.executeQuery<ElasticTransaction> {
                index = "transactions"
                must {
                    term {
                        name = "receiver"
                        value = elrondConfig.mdexPair
                    }
                    prefix {
                        name = "data"
                        value = CREATE_PAIR
                    }
                }
                sort {
                    field = "timestamp"
                    ascending = false
                }
                pageSize = 1000
            }
        }
        val allTokensDeferred = async(Dispatchers.IO) { AllTokens.cached() }

        val createTransactions = createTransactionsDeferred.await()

        val createScResultsDeferred = async(Dispatchers.IO) {
            ElasticService.executeQuery<ElasticScResult> {
                index = "scresults"
                filter {
                    name = "originalTxHash"
                    createTransactions.data.map { it.id }.map { hash ->
                        filter {
                            value = hash
                        }
                    }
                }
                pageSize = 10000
            }
        }

        val allTokens = allTokensDeferred.await().value.associateBy { it.identifier }

        val createScResults = createScResultsDeferred.await().data.map { it.item }.groupBy { it.originalTxHash }

        createTransactions.data.filter { it.item.status == "success" }.mapNotNull { t ->
            t.item.data?.fromBase64String()?.let { data ->
                val (_, firstId, secondId) = data.split("@")
                    .mapIndexed { index, arg -> if (index > 0) arg.fromHexString() else arg }
                letAll(allTokens[firstId], allTokens[secondId]) { (first, second) ->
                    createScResults[t.id]?.firstOrNull()?.data?.fromBase64String()?.split("@")?.lastOrNull()
                        ?.let { addressHex ->
                            TokenPair(first, second, Address(addressHex).erd)
                        }
                }
            }
        }
    }

    suspend fun getTokenPairDetails(address: String): TokenPair? = coroutineScope {
        val allPairsDeferred = async(Dispatchers.IO) { AllTokenPairs.cached() }
        val allTokensDeferred = async(Dispatchers.IO) { AllTokens.cached() }
        val totalSupplyDeferred = async(Dispatchers.IO) { SCService.vmQueryBigInt(address, "getTotalSupply") }
        val totalFeePercentDeferred = async(Dispatchers.IO) { SCService.vmQueryDouble(address, "getTotalFeePercent") }
        val specialFeePercentDeferred = async(Dispatchers.IO) { SCService.vmQueryDouble(address, "getSpecialFee") }
        val stateDeferred = async(Dispatchers.IO) { SCService.vmQueryInt(address, "getState") }
        val lpTokenDeferred = async(Dispatchers.IO) { SCService.vmQueryString(address, "getLpTokenIdentifier") }

        val allPairs = allPairsDeferred.await()
        val allTokens = allTokensDeferred.await()

        allPairs.value.find { it.address == address }?.copy(
            lpTokenTotalSupply = totalSupplyDeferred.await(),
            totalFeePercent = totalFeePercentDeferred.await()?.div(1000000),
            specialFeePercent = specialFeePercentDeferred.await()?.div(1000000),
            state = stateDeferred.await()?.let { TokenPairState.values().getOrNull(it) },
            lpToken = lpTokenDeferred.await()?.let { lpTokenId -> allTokens.value.find { it.identifier == lpTokenId } }
        )
    }

    private const val CREATE_PAIR = "y3jlyxrlugfpcka"
}

@Serializable
data class AllTokenPairs(
    val value: List<TokenPair>
) {
    companion object {
        suspend fun cached() = withCache(CacheType.TokenPairs) { AllTokenPairs(MdexRepository.getAllPairs()) }
    }
}