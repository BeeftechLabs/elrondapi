package com.beeftechlabs.plugins

import com.beeftechlabs.config
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*

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
            println("Call to ${call.request.path()} took ${getTimeMillis() - startTs} ms")
            startTimestamps.remove(key)
        }
    }
}

private fun ApplicationRequest.key() = "$${host()}:${path()}"

fun startCustomTrace(name: String) {
    if (config.traceCalls) {
        startTimestamps[name.appendigThread()] = getTimeMillis()
    }
}

fun endCustomTrace(name: String) {
    if (config.traceCalls) {
        val key = name.appendigThread()
        startTimestamps[key]?.let { startTs ->
            println("Trace $key took ${getTimeMillis() - startTs} ms")
            startTimestamps.remove(key)
        }
    }
}

private fun String.appendigThread() = "$this:${Thread.currentThread().name}"