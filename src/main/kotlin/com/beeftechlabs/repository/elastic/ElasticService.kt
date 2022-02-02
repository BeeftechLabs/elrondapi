package com.beeftechlabs.repository.elastic

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.beeftechlabs.config
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.repository.elastic.model.ElasticResult
import com.beeftechlabs.util.suspending
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient

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
    private val transport by lazy { RestClientTransport(restClient, JacksonJsonpMapper()) }
    private val esClient by lazy { ElasticsearchAsyncClient(transport) }

    suspend fun <T> executeQuery(query: Query, clazz: Class<T>): ElasticResult<T> {
        val size = minOf(query.pageSize, config.maxPageSize)

        startCustomTrace("$query:$clazz")

        val searchRequest = SearchRequest.Builder()
            .index(query.index)
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
                            field.field(query.sort.field)
                                .order(if (query.sort.ascending) SortOrder.Asc else SortOrder.Desc)
                        }
                    }
                }
            }.size(size)
            .build()

        val result = esClient.search(searchRequest, clazz).suspending()
        val data = result.hits().hits().mapNotNull { it.source() }

        endCustomTrace("$query:$clazz")

        return ElasticResult(
            data = data,
            hasMore = data.size == size
        )
    }

    private fun BoolQuery.Builder.add(field: QueryField): BoolQuery.Builder =
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
                    if (field.children.isNotEmpty()) {
                        filter.terms { terms ->
                            terms.field(field.name)
                                .terms { values -> values.value(field.children.map { FieldValue.of(it.value) }) }
                        }
                    } else {
                        filter.term { term ->
                            term.field(field.name).value { it.stringValue(field.value) }
                        }
                    }
                }
            else -> throw IllegalArgumentException("Cannot place ${field.type} here")
        }

    private fun co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder.add(field: QueryField) =
        when (field.type) {
            QueryFieldType.Term ->
                if (field.children.isNotEmpty()) {
                    terms { terms ->
                        terms.field(field.name)
                            .terms { values -> values.value(field.children.map { FieldValue.of(it.value) }) }
                    }
                } else {
                    term { term -> term.field(field.name).value { it.stringValue(field.value) } }
                }
            QueryFieldType.Prefix ->
                prefix { prefix -> prefix.field(field.name).value(field.value) }
            QueryFieldType.Bool ->
                bool { bool -> field.children.fold(bool) { builder, subfield -> builder.add(subfield) } }
            else -> throw IllegalArgumentException("Cannot place ${field.type} here")
        }
}