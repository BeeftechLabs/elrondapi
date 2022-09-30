package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.model.network.NetworkConfig
import com.beeftechlabs.model.network.NetworkStatus
import com.beeftechlabs.model.smartcontract.ScQueryRequest
import com.beeftechlabs.repository.StakingProviders
import com.beeftechlabs.repository.address.model.AddressSort
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.repository.network.cached
import com.beeftechlabs.repository.node.NodeRepository
import com.beeftechlabs.repository.node.Nodes
import com.beeftechlabs.service.SCService
import com.beeftechlabs.util.tryOrDefault
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Routing.coreNetworkRoutes() {
    if (config.hasElrondConfig) {
        post("/scQuery") {
            val request = call.receive<ScQueryRequest>()

            call.respond(SCService.vmQueryParsed(request))
        }

        get("/network/config") {
            call.respond(NetworkConfig.cached())
        }

        get("/network/status") {
            call.respond(NetworkStatus.cached())
        }

        get("/nodes") {
            withContext(Dispatchers.IO) {
                call.respond(Nodes.all().value)
            }
        }

        get("/stake/{owner}") {
            val owner = call.parameters["owner"]
            val includeBlsKeys = call.request.queryParameters["includeBlsKeys"]?.toBooleanStrictOrNull() ?: false
            if (owner.isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.respond(
                    NodeRepository.getStakesForAddress(owner).let { stakes ->
                        if (includeBlsKeys) {
                            stakes
                        } else {
                            stakes.copy(blsKeys = null)
                        }
                    }
                )
            }
        }
        get("/stake") {
            val includeBlsKeys = call.request.queryParameters["includeBlsKeys"]?.toBooleanStrictOrNull() ?: false
            val sort = tryOrDefault(StakeSort.Stake) {
                StakeSort.valueOf(call.request.queryParameters["sort"] ?: "Stake")
            }
            val nodeCount = call.request.queryParameters["nodeCount"]?.toBooleanStrictOrNull() ?: false
            call.respond(
                NodeRepository.getAllStakes().let { allStakes ->
                    if (includeBlsKeys) {
                        allStakes
                    } else {
                        allStakes.map { it.copy(blsKeys = null) }
                    }.run {
                        when (sort) {
                            StakeSort.Stake -> sortedByDescending { it.staked.denominated }
                            StakeSort.TopUp -> sortedByDescending { it.topUp.denominated }
                            StakeSort.TopUpPerNode -> sortedByDescending { it.topUpPerNode.denominated }
                        }
                    }.run {
                        if (nodeCount) {
                            var currentIdx = 1
                            map {
                                val startIdx = currentIdx
                                currentIdx += it.numNodes
                                it.copy(
                                    nodeStartIdx = startIdx,
                                    nodeEndIdx = currentIdx - 1
                                )
                            }
                        } else {
                            this
                        }
                    }
                }
            )
        }

        if (config.hasElastic) {
            get("/stakingProviders") {
                withContext(Dispatchers.IO) {
                    call.respond(StakingProviders.all().value)
                }
            }

            get("/delegators/{delegationContract}") {
                val delegationContract = call.parameters["delegationContract"]
                val sort =
                    call.request.queryParameters["sort"]?.let {
                        AddressSort.valueOfOrDefault(
                            it,
                            AddressSort.AddressAsc
                        )
                    }
                        ?: AddressSort.AddressAsc
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                val requestId = call.request.queryParameters["requestId"]
                val startingWith = call.request.queryParameters["startingWith"]

                if (delegationContract.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.BadRequest)
                } else {
                    withContext(Dispatchers.IO) {
                        call.respond(
                            ElasticRepository.getDelegators(
                                delegationContract,
                                sort,
                                pageSize,
                                requestId,
                                startingWith
                            )
                        )
                    }
                }
            }
        }
    }
}

enum class StakeSort { Stake, TopUp, TopUpPerNode }
