package com.beeftechlabs.routing

import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.vesting.LockedAmountResponse
import com.beeftechlabs.service.SCService
import com.beeftechlabs.util.fromBase64String
import com.beeftechlabs.util.fromBase64ToHexString
import com.beeftechlabs.util.tokenFromIdentifier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

fun Routing.customScRoutes() {
    get("/lockedAmount") {
        val sc = call.request.queryParameters["sc"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val address = call.request.queryParameters["address"] ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        withContext(Dispatchers.IO) {
            call.run {
                val lockedUntil = async {
                    SCService.vmQuery(
                        contract = sc,
                        function = "getLockTime",
                        args = listOf(Address(address).hex)
                    ).first().fromBase64ToHexString().toLong(16)
                }
                val lockedValue = async {
                    SCService.vmQuery(
                        contract = sc,
                        function = "getLockedAmount",
                        args = listOf(Address(address).hex)
                    ).first().fromBase64ToHexString()
                }
                val lockedToken =
                    SCService.vmQuery(
                        contract = sc,
                        function = "getToken",
                        args = listOf(Address(address).hex)
                    ).first().fromBase64String().tokenFromIdentifier()
                respond(
                    LockedAmountResponse(
                        Value.extractHex(lockedValue.await(), lockedToken) ?: Value.zero(lockedToken),
                        lockedUntil.await()
                    )
                )
            }
        }
    }
}