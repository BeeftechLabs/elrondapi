package com.beeftechlabs.repository.elastic

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.Time
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch.core.OpenPointInTimeRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.beeftechlabs.config
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.repository.elastic.model.ElasticItem
import com.beeftechlabs.repository.elastic.model.ElasticResult
import com.beeftechlabs.util.serializeBody
import com.beeftechlabs.util.suspending
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import kotlin.time.Duration

object ElasticService {

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
    val transport by lazy { RestClientTransport(restClient, JacksonJsonpMapper()) }
    val esClient by lazy { ElasticsearchAsyncClient(transport) }

    suspend inline fun <reified T> executeQuery(init: Query.() -> Unit): ElasticResult<T> {
        val query = Query().apply(init)

        startCustomTrace("$query:${T::class}")

        var pitId: String? = null

        val searchRequest = SearchRequest.Builder()
            .apply {
                if (!query.hasPit()) {
                    index(query.index)
                }
            }
            .apply {
                if (query.query.isNotEmpty()) {
                    query { builder ->
                        builder.bool {
                            query.query.fold(it) { b, field ->
                                b.add(field)
                            }
                        }
                    }
                }
            }.apply {
                if (query.hasSort()) {
                    sort { sort ->
                        sort.field { field ->
                            field.field(query.sort.name)
                                .order(
                                    if (query.sort.order == com.beeftechlabs.repository.elastic.SortOrder.Asc)
                                        SortOrder.Asc else SortOrder.Desc
                                )
                        }
                    }
                }
            }.apply {
                query.pit?.let { pitConfig ->
                    pitId = pitConfig.id.takeIf { it.isNotEmpty() } ?: createPit(query.index, pitConfig.length)
                    pit { pit ->
                        pit.id(pitId)
                            .keepAlive(Time.Builder().time("${pitConfig.length.inWholeMinutes}m").build())
                    }
                }
            }.apply {
                if (query.hasSearchAfter()) {
                    searchAfter(query.searchAfter)
                }
            }
            .size(query.size)
            .build()

        if (config.traceCalls) {
            println("Querying index ${query.index}: ${searchRequest.serializeBody(transport)}")
        }

        val result = esClient.search(searchRequest, T::class.java).suspending()
        val data: List<ElasticItem<T>> =
            result.hits().hits().mapNotNull { hit -> hit.source()?.let { ElasticItem(hit.id(), it) } }

        endCustomTrace("$query:${T::class}")

        return ElasticResult(
            data = data,
            hasMore = data.size == query.size,
            pitId = pitId
        )
    }

    fun BoolQuery.Builder.add(field: QueryField<*>): BoolQuery.Builder =
        when (field.type) {
            QueryFieldType.Should ->
                field.children.fold(this) { builder, subfield ->
                    builder.should {
                        it.add(subfield)
                    }
                }
            QueryFieldType.Must ->
                field.children.fold(this) { builder, subfield ->
                    builder.must {
                        it.add(subfield)
                    }
                }
            QueryFieldType.Filter ->
                filter { filter ->
                    when (field) {
                        is QueryField.RangeQueryField -> {
                            if (field.children.isNotEmpty()) {
                                filter.range { range ->
                                    field.children.fold(range) { builder, child ->
                                        when (child) {
                                            is QueryField.RangeQueryField -> {
                                                builder.field(field.name)
                                                    .directionToElastic(child.direction, child.value)
                                            }
                                            else -> throw IllegalArgumentException()
                                        }
                                    }
                                }
                            } else {
                                filter.range { range ->
                                    range.field(field.name).directionToElastic(field.direction, field.value)
                                }
                            }
                        }
                        is QueryField.StringQueryField -> {
                            if (field.children.isNotEmpty()) {
                                field.children.filter { it.type == QueryFieldType.Regex }.firstOrNull()?.let { regexField ->
                                    filter.regexp { regex ->
                                        regex.field(regexField.name).value(
                                            when (regexField) {
                                                is QueryField.StringQueryField -> regexField.value
                                                else -> throw IllegalArgumentException()
                                            }
                                        )
                                    }
                                } ?: filter.terms { terms ->
                                    terms.field(field.name)
                                        .terms { values ->
                                            values.value(field.children.map { child ->
                                                when (child) {
                                                    is QueryField.StringQueryField -> FieldValue.of(child.value)
                                                    is QueryField.LongQueryField -> FieldValue.of(child.value)
                                                    else -> throw IllegalArgumentException()
                                                }
                                            })
                                        }
                                }
                            } else {
                                filter.term { term ->
                                    term.field(field.name).value { it.stringValue(field.value) }
                                }
                            }
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
            else -> throw IllegalArgumentException("Cannot place ${field.type} here")
        }

    private fun co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder.add(field: QueryField<*>) =
        when (field.type) {
            QueryFieldType.Term ->
                if (field.children.isNotEmpty()) {
                    terms { terms ->
                        terms.field(field.name)
                            .terms { values ->
                                values.value(field.children.map { child ->
                                    when (child) {
                                        is QueryField.StringQueryField -> FieldValue.of(child.value)
                                        is QueryField.LongQueryField -> FieldValue.of(child.value)
                                        else -> throw IllegalArgumentException()
                                    }
                                })
                            }
                    }
                } else {
                    term { term ->
                        term.field(field.name).value {
                            when (field) {
                                is QueryField.StringQueryField -> it.stringValue(field.value)
                                is QueryField.LongQueryField -> it.longValue(field.value)
                                else -> throw IllegalArgumentException()
                            }
                        }
                    }
                }
            QueryFieldType.Prefix ->
                prefix { prefix ->
                    when (field) {
                        is QueryField.StringQueryField -> prefix.field(field.name).value(field.value)
                        else -> throw IllegalArgumentException()
                    }
                }
            QueryFieldType.Bool ->
                bool { bool -> field.children.fold(bool) { builder, subfield -> builder.add(subfield) } }
            else -> throw IllegalArgumentException("Cannot place ${field.type} here")
        }

    suspend fun createPit(index: String, length: Duration): String {
        val keepAlive = Time.Builder().time("${length.inWholeMinutes}m").build()
        return esClient.openPointInTime(
            OpenPointInTimeRequest.Builder().index(index).keepAlive(keepAlive).build()
        ).suspending().id()
    }
}

private inline fun <reified T> RangeQuery.Builder.directionToElastic(direction: RangeDirection, value: T) =
    when (direction) {
        RangeDirection.Gte -> gte(JsonData.of(value))
        RangeDirection.Lte -> lte(JsonData.of(value))
        RangeDirection.Gt -> gt(JsonData.of(value))
        RangeDirection.Lt -> lt(JsonData.of(value))
    }