package com.beeftechlabs.plugins

import com.beeftechlabs.config
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = if (config.traceCalls) Level.TRACE else Level.ERROR
        filter { call -> call.request.path().startsWith("/") }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            val path = call.request.path()
            "Path: $path, Status: $status, HTTP method: $httpMethod, User agent: $userAgent"
        }
    }
}
