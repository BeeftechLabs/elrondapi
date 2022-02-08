package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.model.network.NetworkConfig
import com.beeftechlabs.model.network.NetworkStatus
import com.beeftechlabs.repository.Nodes
import com.beeftechlabs.repository.StakingProviders
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.repository.network.cached
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Routing.coreNetworkRoutes() {
    if (config.hasElrondConfig) {
        get("/network/config") {
            call.respond(NetworkConfig.cached())
        }

        get("/network/status") {
            call.respond(NetworkStatus.cached())
        }

        if (config.hasElastic) {
            get("/nodes") {
                withContext(Dispatchers.IO) {
                    call.respond(Nodes.all().value)
                }
            }

            get("/stakingProviders") {
                withContext(Dispatchers.IO) {
                    call.respond(StakingProviders.all().value)
                }
            }

            get("/delegators/{delegationContract}") {
                val delegationContract = call.parameters["delegationContract"]
                if (delegationContract.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.BadRequest)
                } else {
                    withContext(Dispatchers.IO) {
                        call.respond(ElasticRepository.getDelegators(delegationContract))
                    }
                }
            }
        }
    }
}