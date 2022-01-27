package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.model.network.NetworkConfig
import com.beeftechlabs.model.network.NetworkStatus
import com.beeftechlabs.repository.Nodes
import com.beeftechlabs.repository.StakingProviders
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.repository.network.cached
import com.beeftechlabs.repository.token.AllNfts
import com.beeftechlabs.repository.token.AllSfts
import com.beeftechlabs.repository.token.AllTokens
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
                call.respond(AllTokens.cached().value)
            }
        }

        get("/tokens/{address}") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(TokenRepository.getTokensForAddress(address))
                }
            }
        }

        get("/nfts") {
            withContext(Dispatchers.IO) {
                call.respond(AllNfts.cached().value)
            }
        }

        get("/nfts/{address}") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(TokenRepository.getNftsForAddress(address))
                }
            }
        }

        get("/sfts") {
            withContext(Dispatchers.IO) {
                call.respond(AllSfts.cached().value)
            }
        }

        get("/sfts/{address}") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(TokenRepository.getSftsForAddress(address))
                }
            }
        }
    }
}