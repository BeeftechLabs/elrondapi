package com.beeftechlabs.repository.elastic

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.Time
import co.elastic.clients.elasticsearch.core.OpenPointInTimeRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.beeftechlabs.config
import com.beeftechlabs.model.address.AddressDelegation
import com.beeftechlabs.model.address.AddressesResponse
import com.beeftechlabs.model.address.SimpleAddressDetails
import com.beeftechlabs.model.core.Delegator
import com.beeftechlabs.model.core.StakingProvider
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
import com.beeftechlabs.util.suspending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient

object ElasticRepository {

    private val elasticConfig by lazy { config.elastic!! }

    private val credentialsProvider = BasicCredentialsProvider().apply {
        setCredentials(
            AuthScope.ANY, UsernamePasswordCredentials(
                elasticConfig.username, elasticConfig.password
            )
        )
    }

    private val restClient by lazy {
        RestClient.builder(HttpHost.create(elasticConfig.url))
            .setHttpClientConfigCallback { builder ->
                builder.setDefaultCredentialsProvider(credentialsProvider)
            }
            .build()
    }
    private val transport by lazy { RestClientTransport(restClient, JacksonJsonpMapper()) }
    private val esClient by lazy { ElasticsearchAsyncClient(transport) }

    suspend fun getTransaction(hash: String, process: Boolean): Transaction? {
        startCustomTrace("GetSingleTransactionFromElastic:$hash")
        val searchRequest = SearchRequest.Builder()
            .index("transactions")
            .query { q ->
                q.bool { b ->
                    b.must { m ->
                        m.term { t ->
                            t.field("_id")
                                .value { v -> v.stringValue(hash) }
                        }
                    }
                }
            }
            .build()

        val response = esClient.search(searchRequest, ElasticTransaction::class.java).suspending()
        val transaction =
            response.hits().hits().firstNotNullOfOrNull { hit -> hit.source()?.let { Pair(hit.id(), it) } }
                ?.let { (hash, et) ->
                    et.toTransaction(hash)
                }
        endCustomTrace("GetSingleTransactionFromElastic:$hash")

        if (transaction != null && process) {
            startCustomTrace("GetSingleTransactionScResultsFromElastic:$hash")
            return transaction.copy(scResults = getScResultsForTransaction(hash)).also {
                endCustomTrace("GetSingleTransactionScResultsFromElastic:$hash")
            }
        }
        return transaction
    }

    suspend fun getTransactions(request: TransactionsRequest): TransactionsResponse {
        startCustomTrace("GetTransactionsFromElastic:${request.address}")
        startCustomTrace("GetTransactionsFullyFromElastic:${request.address}")
        val maxSize = minOf(request.pageSize, config.maxPageSize)

        val result = ElasticService.executeQuery<ElasticTransaction> {
            index = "transactions"
            size = maxSize
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
            if (request.startTimestamp > 0) {
                filterRange {
                    name = "timestamp"
                    value = request.startTimestamp
                    direction = if (request.newer) RangeDirection.Gte else RangeDirection.Lte
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

        val maxTs = transactions.maxOf { it.timestamp }
        val minTs = transactions.minOf { it.timestamp }

        val updatedTransactions = if (request.includeScResults || request.processTransactions) {
            var transactionsWithScResults = transactions
            startCustomTrace("GetTransactionsScResultsFaster:${request.address}")
            val allScResults = getScResultsForQuery(request, minTs, maxTs)
            endCustomTrace("GetTransactionsScResultsFaster:${request.address}")

//            startCustomTrace("GetTransactionsScResultsFromElastic:${request.address}")
//            val allScResults = getScResultsForTransactionsOneRequest(transactions.filter { it.hasScResults }.map { it.hash })
//            endCustomTrace("GetTransactionsScResultsFromElastic:${request.address}")

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

    private suspend fun getScResultsForTransactionsOneRequest(hashes: List<String>): List<ScResult> {
        val searchRequest = SearchRequest.Builder()
            .index("scresults")
            .query { q ->
                q.bool { b ->
                    b.filter { f ->
                        f.terms { t ->
                            t.field("originalTxHash")
                                .terms { t2 ->
                                    t2.value(hashes.map { FieldValue.of(it) })
                                }
                        }
                    }
                }
            }
            .sort { sort ->
                sort.field { field ->
                    field.field("timestamp")
                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)
                }
            }
            .size(10000)
            .build()

        val response = esClient.search(searchRequest, ElasticScResult::class.java).suspending()
        return response.hits().hits().mapNotNull { hit ->
            hit.source()?.toScResult(hit.id())
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
            filterRange {
                name = "timestamp"
                filterRange {
                    value = minTs
                    direction = RangeDirection.Gte
                }
                filterRange {
                    value = maxTs
                    direction = RangeDirection.Lte
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
        val searchRequest = SearchRequest.Builder()
            .index("delegators")
            .query { q ->
                q.bool { b ->
                    b.must { m ->
                        m.term { t ->
                            t.field("address")
                                .value { v -> v.stringValue(address) }
                        }
                    }
                }
            }
            .build()

        val response = esClient.search(searchRequest, ElasticDelegation::class.java).suspending()
        return response.hits().hits().mapNotNull { hit ->
            hit.source()?.let { delegation ->
                AddressDelegation(
                    stakingProvider = StakingProvider(address = delegation.contract, ""),
                    value = Value.extract(delegation.activeStake, "EGLD"),
                    claimable = Value.None,
                    totalRewards = Value.None
                )
            }
        }
    }

    suspend fun getDelegators(contractAddress: String): List<Delegator> {
        val searchRequest = SearchRequest.Builder()
            .index("delegators")
            .query { q ->
                q.bool { b ->
                    b.must { m ->
                        m.term { t ->
                            t.field("contract")
                                .value { v -> v.stringValue(contractAddress) }
                        }
                    }
                }
            }
            .build()

        val response = esClient.search(searchRequest, ElasticDelegation::class.java).suspending()
        return response.hits().hits().mapNotNull { hit ->
            hit.source()?.let { delegation ->
                Delegator(
                    address = delegation.address,
                    value = Value.extract(delegation.activeStake, "EGLD")
                )
            }
        }
    }

    suspend fun getAddressesPaged(
        sort: AddressSort,
        requestedPageSize: Int,
        filter: String?,
        requestId: String?,
        startingWith: String?
    ): AddressesResponse {
        val pitId = requestId ?: createPit("accounts")
        val maxSize = minOf(requestedPageSize, config.maxPageSize)

        // TODO: switch this to ElasticService DSL (add PIT support to it)

        val searchRequest = SearchRequest.Builder()
            .apply {
                if (filter != null) {
                    query { q ->
                        q.bool { b ->
                            b.filter { f ->
                                f.regexp { r ->
                                    r.field("address")
                                        .value(".*$filter.*")
                                }
                            }
                        }
                    }
                }
            }
            .sort { s ->
                s.field { f ->
                    when (sort) {
                        AddressSort.AddressAsc -> f.field("_id").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)
                        AddressSort.AddressDesc -> f.field("_id").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                        AddressSort.BalanceAsc -> f.field("balanceNum").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)
                        AddressSort.BalanceDesc -> f.field("balanceNum").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                    }
                }
            }
            .size(maxSize)
            .pit { p ->
                p.id(pitId).keepAlive(DEFAULT_PIT_LENGTH)
            }
//            .trackTotalHits(TrackHits.Builder().enabled(false).build())
            .apply {
                if (startingWith != null) {
                    searchAfter(startingWith)
                }
            }
            .build()

        val response = esClient.search(searchRequest, ElasticAddress::class.java).suspending()
        val addresses = response.hits().hits().mapNotNull { it.source() }

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
                        balance = Value(it.balance, it.balanceNum, "EGLD")
                    )
                },
                hasMore = addresses.size == maxSize,
                requestId = response.pitId() ?: pitId,
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

    private suspend fun createPit(index: String, keepAlive: Time = DEFAULT_PIT_LENGTH): String =
        esClient.openPointInTime(
            OpenPointInTimeRequest.Builder().index(index).keepAlive(keepAlive).build()
        ).suspending().id()

    private val DEFAULT_PIT_LENGTH = Time.Builder().time("1m").build()
}