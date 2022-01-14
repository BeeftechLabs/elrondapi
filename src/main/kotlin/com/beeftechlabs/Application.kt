package com.beeftechlabs

import com.beeftechlabs.cache.CacheInitializer
import com.beeftechlabs.plugins.configureHTTP
import com.beeftechlabs.plugins.configureMonitoring
import com.beeftechlabs.plugins.configureRouting
import com.beeftechlabs.plugins.configureSerialization
import com.sksamuel.hoplite.ConfigLoader
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File

val config = ConfigLoader().loadConfigOrThrow<Config>(File("config.yaml"))

fun main() {
    CacheInitializer.initialize()

    embeddedServer(Netty, port = config.port) {
        configureRouting()
        configureSerialization()
        configureMonitoring()
        configureHTTP()
    }.start(wait = true)
}
