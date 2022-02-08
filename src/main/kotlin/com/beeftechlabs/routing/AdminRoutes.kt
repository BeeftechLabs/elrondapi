package com.beeftechlabs.routing

import com.beeftechlabs.cache.RedisStore
import com.beeftechlabs.config
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Routing.adminRoutes() {
    get("/admin/clearRedis") {
        val secret = call.request.queryParameters["secret"]

        if (secret == config.secret) {
            RedisStore.clear()
            call.response.status(HttpStatusCode.OK)
        } else {
            call.response.status(HttpStatusCode.Unauthorized)
        }
    }
}