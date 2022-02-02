package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.repository.address.AddressRepository
import com.beeftechlabs.repository.address.model.AddressSort
import com.beeftechlabs.repository.token.TokenRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Routing.addressRoutes() {
    if (config.hasElrondConfig) {
        get("/addresses") {
            val sort =
                call.request.queryParameters["sort"]?.let { AddressSort.valueOfOrDefault(it, AddressSort.AddressAsc) }
                    ?: AddressSort.AddressAsc
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
            val filter = call.request.queryParameters["filter"]
            val requestId = call.request.queryParameters["requestId"]
            val startingWith = call.request.queryParameters["startingWith"]

            withContext(Dispatchers.IO) {
                call.respond(AddressRepository.getAddresses(sort, pageSize, filter, requestId, startingWith))
            }
        }

        get("/addresses/{address}") {
            val address = call.parameters["address"]
            val withDelegations = call.request.queryParameters["withDelegations"]?.toBooleanStrictOrNull() ?: false
            val withTokens = call.request.queryParameters["withTokens"]?.toBooleanStrictOrNull() ?: false
            val withNfts = call.request.queryParameters["withNfts"]?.toBooleanStrictOrNull() ?: false
            val withSfts = call.request.queryParameters["withSfts"]?.toBooleanStrictOrNull() ?: false
            val withStake = call.request.queryParameters["withStake"]?.toBooleanStrictOrNull() ?: false
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(
                        AddressRepository.getAddressDetails(
                            address,
                            withDelegations,
                            withTokens,
                            withNfts,
                            withSfts,
                            withStake
                        )
                    )
                }
            }
        }

        get("/addresses/{address}/balance") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(AddressRepository.getAddressBalance(address))
                }
            }
        }

        get("/addresses/{address}/nonce") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(AddressRepository.getAddressNonce(address))
                }
            }
        }

        get("/addresses/{address}/tokens") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(TokenRepository.getTokensForAddress(address))
                }
            }
        }

        get("/addresses/{address}/nfts") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(TokenRepository.getNftsForAddress(address))
                }
            }
        }

        get("/addresses/{address}/sfts") {
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