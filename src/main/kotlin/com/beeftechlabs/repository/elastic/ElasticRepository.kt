package com.beeftechlabs.repository.elastic

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.Time
import co.elastic.clients.elasticsearch.core.OpenPointInTimeRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.beeftechlabs.config
import com.beeftechlabs.model.address.AddressDelegation
import com.beeftechlabs.model.address.AddressesResponse
import com.beeftechlabs.model.address.SimpleAddressDetails
import com.beeftechlabs.model.core.Delegator
import com.beeftechlabs.model.core.StakingProvider
import com.beeftechlabs.model.token.TokenRequest
import com.beeftechlabs.model.token.TokenResponse
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.*
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.processing.TransactionProcessor
import com.beeftechlabs.repository.address.model.AddressSort
import com.beeftechlabs.repository.elastic.model.*
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
        val size = minOf(request.pageSize, config.maxPageSize)
        val searchRequest = SearchRequest.Builder()
            .index("transactions")
            .query { q ->
                q.bool { b ->
                    b.must { m ->
                        m.bool { b ->
                            b.should { s ->
                                s.term { t ->
                                    t.field("sender")
                                        .value { v -> v.stringValue(request.address) }
                                }
                            }
                                .should { s ->
                                    s.term { t ->
                                        t.field("receiver")
                                            .value { v -> v.stringValue(request.address) }
                                    }
                                }
                        }
                    }.apply {
                        if (request.startTimestamp > 0) {
                            filter { f ->
                                f.range { r ->
                                    r.field("timestamp")
                                        .apply {
                                            if (request.newer) {
                                                gte(JsonData.of(request.startTimestamp))
                                            } else {
                                                lte(JsonData.of(request.startTimestamp))
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }
            .sort { sort ->
                sort.field { field ->
                    field.field("timestamp")
                        .order(SortOrder.Desc)
                }
            }
            .size(size)
            .build()

        val response = esClient.search(searchRequest, ElasticTransaction::class.java).suspending()
        val transactions = response.hits().hits().mapNotNull { hit ->
            hit.source()?.toTransaction(hit.id())
        }

        val updatedTransactions = if (request.includeScResults || request.processTransactions) {

            var transactionsWithScResults = transactions

            val allScResults = getScResultsForTransactions(transactions.map { it.hash })

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
                updatedTransactions.map { TransactionProcessor.process(request.address, it) }
            }
        } else {
            updatedTransactions
        }

        return TransactionsResponse(
            processedTransactions,
            transactions.size == size,
            if (transactions.isNotEmpty()) {
                if (request.newer) transactions.maxOf { it.timestamp } else transactions.minOf { it.timestamp }
            } else {
                request.startTimestamp
            }
        )
    }

    private suspend fun getScResultsForTransaction(hash: String): List<ScResult> {
        val searchRequest = SearchRequest.Builder()
            .index("scresults")
            .query { q ->
                q.bool { b ->
                    b.must { m ->
                        m.term { t ->
                            t.field("originalTxHash")
                                .value { v -> v.stringValue(hash) }
                        }
                    }
                }
            }
            .build()

        val response = esClient.search(searchRequest, ElasticScResult::class.java).suspending()
        return response.hits().hits().mapNotNull { hit ->
            hit.source()?.toScResult(hit.id())
        }
    }

    private suspend fun getScResultsForTransactions(hashes: List<String>): List<ScResult> {
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
            .size(1000)
            .build()

        val response = esClient.search(searchRequest, ElasticScResult::class.java).suspending()
        return response.hits().hits().mapNotNull { hit ->
            hit.source()?.toScResult(hit.id())
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

    suspend fun getTokens(request: TokenRequest): TokenResponse {
        val searchRequest = SearchRequest.Builder()
            .index("tokens")
            .query { q ->
                q.bool { b ->
                    b.apply {
                        request.timestamp?.let {
                            filter { f ->
                                f.range { r ->
                                    r.field("timestamp")
                                        .apply {
                                            if (request.newer) {
                                                gte(JsonData.of(request.timestamp))
                                            } else {
                                                lte(JsonData.of(request.timestamp))
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }
            .size(request.size)
            .sort { sort ->
                sort.field { field ->
                    field.field("timestamp")
                        .order(if (request.newer) SortOrder.Desc else SortOrder.Asc)
                }
            }
            .build()

        val response = esClient.search(searchRequest, ElasticToken::class.java).suspending()
        val tokens = response.hits().hits().mapNotNull { it.source() }

        return TokenResponse(
            tokens = tokens,
            hasMore = tokens.size == request.size,
            lastTimestamp = tokens.firstOrNull()?.timestamp
        )
    }

    suspend fun getAddressesPaged(
        sort: AddressSort,
        filter: String?,
        requestId: String?,
        startingWith: String?
    ): AddressesResponse {
        val pitId = requestId ?: createPit("accounts")

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
                        AddressSort.AddressAsc -> f.field("_id").order(SortOrder.Asc)
                        AddressSort.AddressDesc -> f.field("_id").order(SortOrder.Desc)
                        AddressSort.BalanceAsc -> f.field("balanceNum").order(SortOrder.Asc)
                        AddressSort.BalanceDesc -> f.field("balanceNum").order(SortOrder.Desc)
                    }
                }
            }
            .size(NUM_ADDRESSES_PER_PAGE)
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
                hasMore = addresses.size == NUM_ADDRESSES_PER_PAGE,
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

    private const val NUM_ADDRESSES_PER_PAGE = 20
    private val DEFAULT_PIT_LENGTH = Time.Builder().time("1m").build()
}