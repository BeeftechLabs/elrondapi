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
    }

}
