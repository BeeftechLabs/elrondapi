package com.beeftechlabs

import com.beeftechlabs.plugins.configureHTTP
import com.beeftechlabs.plugins.configureMonitoring
import com.beeftechlabs.plugins.configureRouting
import com.beeftechlabs.plugins.configureSerialization
import com.sksamuel.hoplite.ConfigLoader
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.io.File

val config = ConfigLoader().loadConfigOrThrow<Config>(
    listOfNotNull(
        File("config-dev.yaml").takeIf { it.exists() },
        File("config.yaml")
    )
)

fun main() {
    embeddedServer(CIO, port = config.port) {
        configureRouting()
        configureSerialization()
        configureMonitoring()
        configureHTTP()
    }.start(wait = true)
}
