package com.beeftechlabs.repository

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.beeftechlabs.config
import com.beeftechlabs.model.*
import com.beeftechlabs.processing.TransactionProcessor
import com.beeftechlabs.util.suspending
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient

object TransactionRepository {

    private val credentialsProvider = BasicCredentialsProvider().apply {
        setCredentials(
            AuthScope.ANY, UsernamePasswordCredentials(
                config.elastic.username, config.elastic.password
            )
        )
    }

    private val restClient by lazy {
        RestClient.builder(HttpHost.create(config.elastic.url))
            .setHttpClientConfigCallback { builder ->
                builder.setDefaultCredentialsProvider(credentialsProvider)
            }
            .build()
    }
    private val transport by lazy { RestClientTransport(restClient, JacksonJsonpMapper()) }
    private val esClient by lazy { ElasticsearchAsyncClient(transport) }

    suspend fun getTransaction(hash: String, process: Boolean): Transaction? {
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

        if (transaction != null && process) {
            return transaction.copy(scResults = getScResultsForTransaction(hash))
        }
        return transaction
    }

    suspend fun getTransactions(request: TransactionsRequest): TransactionsResponse {
        val start = getTimeMillis()
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

        println("Fetching transactions took ${getTimeMillis() - start} millis")

        val updatedTransactions = if (request.includeScResults || request.processTransactions) {
            val startSc = getTimeMillis()

            var transactionsWithScResults = transactions

            val scResultsDeferred = transactions.filter { it.hasScResults }.map {
                coroutineScope { async { getScResultsForTransaction(it.hash) } }
            }
            val allScResults = scResultsDeferred.awaitAll()

            allScResults.forEach { scResults ->
                if (scResults.isNotEmpty()) {
                    val hash = scResults.first().originalTxHash
                    transactionsWithScResults = transactionsWithScResults.map {
                        if (it.hash == hash) {
                            it.copy(scResults = scResults)
                        } else {
                            it
                        }
                    }
                }
            }

            // TODO: fix this
//            val allScResults = getScResultsForTransactions(transactions.map { it.hash })
//
//            allScResults.groupBy { it.originalTxHash }.forEach { entry ->
//                transactionsWithScResults = transactionsWithScResults.map {
//                    if (it.hash == entry.key) {
//                        it.copy(scResults = entry.value)
//                    } else {
//                        it
//                    }
//                }
//            }

            println("Fetching scResults took ${getTimeMillis() - startSc} millis")

            transactionsWithScResults
        } else {
            transactions
        }

        val processedTransactions = if (request.processTransactions) {
            withContext(Dispatchers.Default) {
                val startProcess = getTimeMillis()
                updatedTransactions.map { TransactionProcessor.process(request.address, it) }.also {
                    println("Processing transactions took ${getTimeMillis() - startProcess} millis")
                }
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
                    b.must { m ->
                        m.bool { builder ->
                            hashes.drop(1).fold(
                                builder.should { s ->
                                    s.term { t ->
                                        t.field("originalTxHash")
                                            .value { v -> v.stringValue(hashes.first()) }
                                    }
                                }
                            ) { acc, hash ->
                                acc.should { s ->
                                    s.term { t ->
                                        t.field("originalTxHash")
                                            .value { v -> v.stringValue(hash) }
                                    }
                                }
                            }
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
}