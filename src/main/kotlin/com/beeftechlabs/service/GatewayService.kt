package com.beeftechlabs.service

import com.beeftechlabs.api.HttpClientFactory
import com.beeftechlabs.config
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.model.smartcontract.ScQueryResponse
import io.ktor.client.call.*
import io.ktor.client.request.*

object GatewayService {

    val elrondConfig by lazy { config.elrond!! }

    val client by lazy { HttpClientFactory.create() }

    suspend inline fun <reified T> get(path: String): T = client.get {
        url("${elrondConfig.proxy}/$path")
    }.body()

    suspend inline fun <reified T> post(path: String, request: Any): T = client.post {
        url("${elrondConfig.proxy}/$path")
        setBody(request)
    }.body()

    suspend fun vmQuery(scQueryRequest: ScQueryRequest): ScQueryResponse =
        client.post {
            url("${elrondConfig.proxy}/vm-values/query")
            setBody(scQueryRequest)
        }.body()
}