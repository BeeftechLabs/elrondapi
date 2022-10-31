package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.repository.token.Nfts
import com.beeftechlabs.repository.token.Sfts
import com.beeftechlabs.repository.token.Esdts
import com.beeftechlabs.repository.token.TokenRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Routing.tokenRoutes() {
    if (config.hasElrondConfig) {
        get("/tokens") {
            withContext(Dispatchers.IO) {
                call.respond(Esdts.all().value)
            }
        }

        get("/tokens/{identifier}") {
            val identifier = call.parameters["identifier"]
            if (identifier.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    TokenRepository.getEsdtWithId(identifier)?.let {
                        call.respond(it)
                    } ?: call.response.status(HttpStatusCode.NotFound)
                }
            }
        }

        get("/nfts") {
            withContext(Dispatchers.IO) {
                call.respond(Nfts.all().value)
            }
        }

        get("/nfts/{identifier}") {
            val identifier = call.parameters["identifier"]
            if (identifier.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    TokenRepository.getNftWithCollectionId(identifier)?.let {
                        call.respond(it)
                    } ?: call.response.status(HttpStatusCode.NotFound)
                }
            }
        }

        get("/sfts") {
            withContext(Dispatchers.IO) {
                call.respond(Sfts.all().value)
            }
        }

        get("/sfts/{identifier}") {
            val identifier = call.parameters["identifier"]
            if (identifier.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    TokenRepository.getSftWithId(identifier)?.let {
                        call.respond(it)
                    } ?: call.response.status(HttpStatusCode.NotFound)
                }
            }
        }
    }
}