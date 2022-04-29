package com.beeftechlabs.zoidpay.routing

import com.beeftechlabs.config
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.repository.address.CoreAddressRepository
import com.beeftechlabs.zoidpay.repository.StakingRepository
import com.beeftechlabs.zoidpay.stake.CreateTransactionUsecase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val zoidPayConfig by lazy { config.zoidpay!! }

fun Routing.zoidpayRoutes() {

    if (config.zoidpay != null) {
        get("/zoidpay/{pool}/delegators") {
            val pool = call.parameters["pool"]
            if (pool.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(StakingRepository.getDelegators(pool))
                }
            }
        }

        get("/zoidpay/{pool}/delegatorsStakes") {
            val pool = call.parameters["pool"]
            val start = call.request.queryParameters["start"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 25
            if (pool.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(StakingRepository.getStakes(pool, start, size))
                }
            }
        }

        get("/zoidpay/{address}/stakes") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(StakingRepository.getStakesForDelegator(address))
                }
            }
        }

        get("/zoidpay/{address}/claimable") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(StakingRepository.getClaimableRewards(address))
                }
            }
        }

        get("/zoidpay/{address}/claimablePerStake") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    call.respond(StakingRepository.getClaimableRewardsPerStake(address))
                }
            }
        }

        get("/zoidpay/pools") {
            withContext(Dispatchers.IO) {
                call.respond(StakingRepository.getPools())
            }
        }

        get("/zoidpay/{address}/transaction/stake") {
            val address = call.parameters["address"]
            val value = call.request.queryParameters["value"]?.toDoubleOrNull()
            val pool = call.request.queryParameters["pool"]
            val months = call.request.queryParameters["months"]?.toIntOrNull()
            val parsedValue = value?.let { Value.extract(it, zoidPayConfig.tokenId) }
            if (address.isNullOrEmpty() || value == null || pool.isNullOrEmpty() || parsedValue == null || months == null) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    val nonce = CoreAddressRepository.getAddressNonce(address).value
                    call.respond(CreateTransactionUsecase.stake(address, pool, parsedValue, months, nonce))
                }
            }
        }

        get("/zoidpay/{address}/transaction/claim") {
            val address = call.parameters["address"]
            if (address.isNullOrEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
            } else {
                withContext(Dispatchers.IO) {
                    val nonce = CoreAddressRepository.getAddressNonce(address).value
                    call.respond(CreateTransactionUsecase.claim(address, nonce))
                }
            }
        }
    }
}