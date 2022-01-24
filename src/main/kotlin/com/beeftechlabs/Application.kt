package com.beeftechlabs

import com.beeftechlabs.plugins.*
import com.sksamuel.hoplite.ConfigLoader
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File

val config = ConfigLoader().loadConfigOrThrow<Config>(
    listOfNotNull(
        File("config-dev.yaml").takeIf { it.exists() },
        File("config.yaml")
    )
)

fun main() {
    embeddedServer(Netty, port = config.port) {
        configureRouting()
        configureSerialization()
        configureMonitoring()
        configureHTTP()
    }.start(wait = true)
}
