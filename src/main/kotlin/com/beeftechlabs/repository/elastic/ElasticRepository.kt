package com.beeftechlabs.repository.elastic

import com.beeftechlabs.config
import com.beeftechlabs.model.address.AddressDelegation
import com.beeftechlabs.model.address.AddressesResponse
import com.beeftechlabs.model.address.SimpleAddressDetails
import com.beeftechlabs.model.core.StakingProvider
import com.beeftechlabs.model.delegation.Delegator
import com.beeftechlabs.model.delegation.DelegatorsResponse
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.*
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.processing.TransactionProcessor
import com.beeftechlabs.repository.address.model.AddressSort
import com.beeftechlabs.repository.elastic.model.ElasticAddress
import com.beeftechlabs.repository.elastic.model.ElasticDelegation
import com.beeftechlabs.repository.elastic.model.ElasticScResult
import com.beeftechlabs.repository.elastic.model.ElasticTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

object ElasticRepository {

    suspend fun getTransaction(hash: String, process: Boolean): Transaction? {
        startCustomTrace("GetSingleTransactionFromElastic:$hash")

        val result = ElasticService.executeQuery<ElasticTransaction> {
            index = "transactions"
            must {
                term {
                    name = "_id"
                    value = hash
                }
            }
        }

        return result.data.firstOrNull()?.let { etransaction ->
            val transaction = etransaction.item.toTransaction(etransaction.id)

            if (process) {
                startCustomTrace("GetSingleTransactionScResultsFromElastic:$hash")
                transaction.copy(scResults = getScResultsForTransaction(hash)).also {
                    endCustomTrace("GetSingleTransactionScResultsFromElastic:$hash")
                }
            } else {
                transaction
            }
        }
    }

    suspend fun getTransactions(request: TransactionsRequest): TransactionsResponse {
        startCustomTrace("GetTransactionsFromElastic:${request.address}")
        startCustomTrace("GetTransactionsFullyFromElastic:${request.address}")
        val maxSize = minOf(request.pageSize, config.maxPageSize)

        val result = ElasticService.executeQuery<ElasticTransaction> {
            index = "transactions"
            size = maxSize
            if (request.address.isNotEmpty()) {
                must {
                    bool {
                        should {
                            term {
                                name = "sender"
                                value = request.address
                            }
                            term {
                                name = "receiver"
                                value = request.address
                            }
                        }
                    }
                }
            }
            if (request.startTimestamp > 0) {
                filter {
                    range {
                        name = "timestamp"
                        value = request.startTimestamp
                        direction = if (request.newer) RangeDirection.Gte else RangeDirection.Lte
                    }
                }
            }
            if (request.dataFilter != null) {
                filter {
                    regex {
                        name = "data"
                        value = request.dataFilter
                    }
                }
            }
            sort {
                name = "timestamp"
                order = SortOrder.Desc
            }
        }

        val transactions = result.data.map { t ->
            t.item.toTransaction(t.id)
        }
        endCustomTrace("GetTransactionsFromElastic:${request.address}")

        if (transactions.isEmpty()) {
            return TransactionsResponse(
                hasMore = false,
                lastTimestamp = 0,
                transactions = emptyList()
            )
        }

        val maxTs = transactions.maxOf { it.timestamp }
        val minTs = transactions.minOf { it.timestamp }

        val updatedTransactions = if (request.includeScResults || request.processTransactions) {
            var transactionsWithScResults = transactions
            startCustomTrace("GetTransactionsScResults:${request.address}")
            val allScResults = if (request.address.isNotEmpty()) {
                // TODO: This doesn't work for all cases
                getScResultsForQuery(request, minTs, maxTs)
//                getScResultsForTransactions(transactions.map { it.hash })
            } else {
                getScResultsForTransactions(transactions.map { it.hash })
            }
            endCustomTrace("GetTransactionsScResults:${request.address}")

            allScResults.groupBy { it.originalTxHash }.forEach { entry ->
                transactionsWithScResults = transactionsWithScResults.map {
                    if (it.hash == entry.key) {
                        it.copy(scResults = entry.value)
                    } else {
                        it
                    }
                }
            }

            transactionsWithScResults
        } else {
            transactions
        }

        val processedTransactions = if (request.processTransactions) {
            withContext(Dispatchers.Default) {
                startCustomTrace("ProcessTransactionsFromElastic:${request.address}")
                updatedTransactions.map { TransactionProcessor.process(it) }.also {
                    endCustomTrace("ProcessTransactionsFromElastic:${request.address}")
                }
            }
        } else {
            updatedTransactions
        }

        return TransactionsResponse(
            transactions.size == maxSize,
            if (transactions.isNotEmpty()) {
                if (request.newer) maxTs else minTs
            } else {
                request.startTimestamp
            },
            processedTransactions
        ).also {
            endCustomTrace("GetTransactionsFullyFromElastic:${request.address}")
        }
    }

    private suspend fun getScResultsForQuery(request: TransactionsRequest, minTs: Long, maxTs: Long): List<ScResult> {
        val result = ElasticService.executeQuery<ElasticScResult> {
            index = "scresults"
            size = 10000
            must {
                bool {
                    should {
                        term {
                            name = "sender"
                            value = request.address
                        }
                        term {
                            name = "receiver"
                            value = request.address
                        }
                    }
                }
            }
            filter {
                name = "timestamp"
                range {
                    value = minTs
                    direction = RangeDirection.Gte
                }
                range {
                    value = maxTs
                    direction = RangeDirection.Lte
                }
            }
        }

        return result.data.map { elasticScResult ->
            elasticScResult.item.toScResult(elasticScResult.id)
        }
    }

    private suspend fun getScResultsForTransactions(hashes: List<String>): List<ScResult> {
        val result = ElasticService.executeQuery<ElasticScResult> {
            index = "scresults"
            size = 10000
            must {
                term {
                    name = "originalTxHash"
                    hashes.forEach {
                        term {
                            value = it
                        }
                    }
                }
            }
        }

        return result.data.map { elasticScResult ->
            elasticScResult.item.toScResult(elasticScResult.id)
        }
    }

    private suspend fun getScResultsForTransaction(hash: String): List<ScResult> {
        val result = ElasticService.executeQuery<ElasticScResult> {
            index = "scresults"
            size = 100
            filter {
                name = "originalTxHash"
                value = hash
            }
            sort {
                name = "timestamp"
                order = SortOrder.Asc
            }
        }

        return result.data.map { elasticScResult ->
            elasticScResult.item.toScResult(elasticScResult.id)
        }
    }

    suspend fun getDelegations(address: String): List<AddressDelegation> {
        val result = ElasticService.executeQuery<ElasticDelegation> {
            index = "delegators"
            must {
                term {
                    name = "address"
                    value = address
                }
            }
        }

        return result.data.map { delegation ->
            AddressDelegation(
                stakingProvider = StakingProvider(address = delegation.item.contract, ""),
                value = Value.extract(delegation.item.activeStake, "EGLD") ?: Value.zeroEgld(),
                claimable = Value.None,
                totalRewards = Value.None
            )
        }
    }

    suspend fun getDelegators(
        contractAddress: String,
        sort: AddressSort,
        requestedPageSize: Int,
        requestId: String?,
        startingWith: String?
    ): DelegatorsResponse {
        val maxSize = minOf(requestedPageSize, config.maxPageSize)

        val result = ElasticService.executeQuery<ElasticDelegation> {
            index = "delegators"
            size = maxSize
            must {
                term {
                    name = "contract"
                    value = contractAddress
                }
            }
            filter {
                range {
                    name = "activeStakeNum"
                    value = 0
                    direction = RangeDirection.Gt
                }
            }
            when (sort) {
                AddressSort.AddressAsc -> sort { name = "_id"; order = SortOrder.Asc }
                AddressSort.AddressDesc -> sort { name = "_id"; order = SortOrder.Desc }
                AddressSort.BalanceAsc -> sort { name = "activeStakeNum"; order = SortOrder.Asc }
                AddressSort.BalanceDesc -> sort { name = "activeStakeNum"; order = SortOrder.Desc }
            }
            pit {
                id = requestId ?: ""
                length = 1.minutes
            }
            startingWith?.let { searchAfter = it }
        }

        val delegators = result.data.map { delegation ->
            Delegator(
                address = delegation.item.address,
                value = Value.extract(delegation.item.activeStake, "EGLD") ?: Value.zeroEgld()
            )
        }

        if (delegators.isNotEmpty()) {
            val firstResult = when (sort) {
                AddressSort.AddressAsc, AddressSort.AddressDesc -> delegators.first().address
                AddressSort.BalanceAsc, AddressSort.BalanceDesc -> delegators.first().value.denominated?.toString()
            }

            val lastResult = when (sort) {
                AddressSort.AddressAsc, AddressSort.AddressDesc -> delegators.last().address
                AddressSort.BalanceAsc, AddressSort.BalanceDesc -> delegators.last().value.denominated?.toString()
            }

            return DelegatorsResponse(
                delegators = delegators,
                hasMore = delegators.size == maxSize,
                requestId = result.pitId,
                firstResult = firstResult,
                lastResult = lastResult
            )
        } else {
            return DelegatorsResponse(
                delegators = emptyList(),
                hasMore = false
            )
        }
    }

    suspend fun getAddressesPaged(
        sort: AddressSort,
        requestedPageSize: Int,
        filter: String?,
        requestId: String?,
        startingWith: String?
    ): AddressesResponse {
        val maxSize = minOf(requestedPageSize, config.maxPageSize)

        val result = ElasticService.executeQuery<ElasticAddress> {
            index = "accounts"
            size = maxSize
            if (filter != null) {
                filter {
                    regex {
                        name = "address"
                        value = ".*$filter.*"
                    }
                }
            }
            when (sort) {
                AddressSort.AddressAsc -> sort { name = "_id"; order = SortOrder.Asc }
                AddressSort.AddressDesc -> sort { name = "_id"; order = SortOrder.Desc }
                AddressSort.BalanceAsc -> sort { name = "balanceNum"; order = SortOrder.Asc }
                AddressSort.BalanceDesc -> sort { name = "balanceNum"; order = SortOrder.Desc }
            }
            pit {
                id = requestId ?: ""
                length = 1.minutes
            }
            startingWith?.let { searchAfter = it }
        }

        val addresses = result.data.map { it.item }

        if (addresses.isNotEmpty()) {

            val firstResult = when (sort) {
                AddressSort.AddressAsc, AddressSort.AddressDesc -> addresses.first().address
                AddressSort.BalanceAsc, AddressSort.BalanceDesc -> addresses.first().balanceNum.toString()
            }

            val lastResult = when (sort) {
                AddressSort.AddressAsc, AddressSort.AddressDesc -> addresses.last().address
                AddressSort.BalanceAsc, AddressSort.BalanceDesc -> addresses.last().balanceNum.toString()
            }

            return AddressesResponse(
                addresses = addresses.map {
                    SimpleAddressDetails(
                        address = it.address,
                        balance = Value(it.balance, 18, it.balanceNum, "EGLD")
                    )
                },
                hasMore = addresses.size == maxSize,
                requestId = result.pitId,
                firstResult = firstResult,
                lastResult = lastResult
            )
        } else {
            return AddressesResponse(
                hasMore = false,
                addresses = emptyList()
            )
        }
    }
}