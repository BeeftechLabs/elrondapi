package com.beeftechlabs

import com.beeftechlabs.plugins.configureHTTP
import com.beeftechlabs.plugins.configureMonitoring
import com.beeftechlabs.plugins.configureRouting
import com.beeftechlabs.plugins.configureSerialization
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun main() {
    embeddedServer(CIO, port = config.port) {
        configureRouting()
        configureSerialization()
        configureMonitoring()
        configureHTTP()
    }.start(wait = true)
}
