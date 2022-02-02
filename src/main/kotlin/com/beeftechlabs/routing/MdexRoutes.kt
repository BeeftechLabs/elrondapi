package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.repository.mdex.AllTokenPairs
import com.beeftechlabs.repository.mdex.MdexRepository
import io.ktor.http.*
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

        get("/mdex/tokenPairs/{address}") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    MdexRepository.getTokenPairDetails(address)?.let {
                        call.respond(it)
                    } ?: call.response.status(HttpStatusCode.NotFound)
                }
            }
        }
    }
}