package com.beeftechlabs.plugins

import com.beeftechlabs.config
import io.ktor.server.plugins.*
import org.slf4j.event.*
import io.ktor.server.application.*
import io.ktor.server.request.*

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = if (config.traceCalls) Level.TRACE else Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

}
