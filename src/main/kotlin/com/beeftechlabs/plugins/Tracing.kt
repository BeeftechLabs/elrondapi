package com.beeftechlabs.plugins

import com.beeftechlabs.config
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import mu.KotlinLogging

private val startTimestamps = mutableMapOf<String, Long>()

fun PipelineContext<Unit, ApplicationCall>.startCallTrace() {
    if (config.traceCalls) {
        startTimestamps[call.request.key()] = getTimeMillis()
    }
}

fun PipelineContext<Unit, ApplicationCall>.endCallTrace() {
    if (config.traceCalls) {
        val key = call.request.key()
        startTimestamps[key]?.let { startTs ->
            logger.trace { "Call to ${call.request.path()} took ${getTimeMillis() - startTs} ms" }
            startTimestamps.remove(key)
        }
    }
}

private fun ApplicationRequest.key() = "$${host()}:${path()}"

fun startCustomTrace(key: String) {
    if (config.traceCalls) {
        startTimestamps[key] = getTimeMillis()
    }
}

fun endCustomTrace(key: String) {
    if (config.traceCalls) {
        startTimestamps[key]?.let { startTs ->
            logger.trace { "Trace $key took ${getTimeMillis() - startTs} ms" }
            startTimestamps.remove(key)
        }
    }
}

private val logger = KotlinLogging.logger("")