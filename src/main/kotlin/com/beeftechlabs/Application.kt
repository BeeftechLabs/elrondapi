package com.beeftechlabs

import com.beeftechlabs.plugins.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun main() {
    embeddedServer(CIO, port = config.port) {
        configureRouting()
        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureCors()
    }.start(wait = true)
}
