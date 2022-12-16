package com.beeftechlabs.api

import com.beeftechlabs.config
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.json.*
import io.ktor.client.plugins.kotlinx.serializer.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*

object HttpClientFactory {

    fun create() = HttpClient(CIO) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = if (config.logApiCalls) { LogLevel.BODY } else { LogLevel.NONE }
        }
        install(JsonPlugin) {
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 60000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
}