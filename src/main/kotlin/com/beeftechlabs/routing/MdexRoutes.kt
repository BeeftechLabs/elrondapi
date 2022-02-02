package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.repository.mdex.AllTokenPairs
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Routing.mdexRoutes() {
    if (config.hasElrondConfig) {
        get("/mdex/tokenPairs") {
            withContext(Dispatchers.IO) {
                call.respond(AllTokenPairs.cached().value)
            }
        }
    }
}