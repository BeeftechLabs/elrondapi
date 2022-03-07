package com.beeftechlabs.plugins

import com.beeftechlabs.routing.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureRouting() {

    routing {
        static {
            resource("/", "index.html")
            resource("/favicon.ico", "favicon.ico")
            resource("/openapi.yaml", "openapi.yaml")
        }

        coreNetworkRoutes()
        addressRoutes()
        transactionRoutes()
        tokenRoutes()
        mdexRoutes()
        adminRoutes()
        customScRoutes()
    }
}
