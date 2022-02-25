package com.beeftechlabs.routing

import com.beeftechlabs.cache.RedisStore
import com.beeftechlabs.config
import com.beeftechlabs.repository.Nodes
import com.beeftechlabs.repository.StakingProviders
import com.beeftechlabs.repository.mdex.TokenPairs
import com.beeftechlabs.repository.token.AllTokenAssets
import com.beeftechlabs.repository.token.Esdts
import com.beeftechlabs.repository.token.Nfts
import com.beeftechlabs.repository.token.Sfts
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

fun Routing.adminRoutes() {

    get("/hello") {
        call.response.status(HttpStatusCode.OK)
    }

    get("/admin/clearRedis") {
        val secret = call.request.queryParameters["secret"]

        if (secret == config.secret) {
            RedisStore.clear()
            call.response.status(HttpStatusCode.OK)
        } else {
            call.response.status(HttpStatusCode.Unauthorized)
        }
    }

    post("/admin/refreshHourlyCache") {
        val refreshRequest = call.receive<RefreshRequest>()

        if (refreshRequest.secret == config.secret) {
            coroutineScope {
                launch(Dispatchers.IO) {
                    Esdts.all(true)
                    TokenPairs.all(true)
                }
                launch(Dispatchers.IO) { Nfts.all(true) }
                launch(Dispatchers.IO) { Sfts.all(true) }
                launch(Dispatchers.IO) { StakingProviders.all(true) }
                launch(Dispatchers.IO) { Nodes.all(true) }
            }

            call.response.status(HttpStatusCode.OK)
        } else {
            call.response.status(HttpStatusCode.Unauthorized)
        }
    }

    post("/admin/refreshTokenAssets") {
        val refreshRequest = call.receive<RefreshRequest>()

        if (refreshRequest.secret == config.secret) {
            coroutineScope {
                launch(Dispatchers.IO) {
                    AllTokenAssets.get(true)
                }
            }

            call.response.status(HttpStatusCode.OK)
        } else {
            call.response.status(HttpStatusCode.Unauthorized)
        }
    }
}

@Serializable
private data class RefreshRequest(
    val secret: String
)