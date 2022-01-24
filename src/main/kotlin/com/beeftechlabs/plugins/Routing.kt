package com.beeftechlabs.plugins

import com.beeftechlabs.config
import com.beeftechlabs.model.network.NetworkConfig
import com.beeftechlabs.model.network.NetworkStatus
import com.beeftechlabs.model.transaction.NewTransaction
import com.beeftechlabs.model.transaction.TransactionsRequest
import com.beeftechlabs.processing.TransactionProcessor
import com.beeftechlabs.repository.Nodes
import com.beeftechlabs.repository.StakingProviders
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.repository.network.cached
import com.beeftechlabs.repository.token.AllNfts
import com.beeftechlabs.repository.token.AllSfts
import com.beeftechlabs.repository.token.AllTokens
import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.repository.token.address.AddressRepository
import com.beeftechlabs.repository.transaction.TransactionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Application.configureRouting() {

    routing {
        static {
            resource("/", "index.html")
            resource("*", "index.html")
            resource("/favicon.ico", "favicon.ico")
            resource("/openapi.yaml", "openapi.yaml")
        }

        if (config.hasElastic) {
            post("/transactions") {
                val request = call.receive<TransactionsRequest>()

                withContext(Dispatchers.IO) {
                    call.respond(TransactionRepository.getTransactions(request))
                }
            }

            get("/transactions/{address}") {
                val address = call.parameters["address"]

                if (address != null) {
                    val request = TransactionsRequest(
                        address = address
                    ).let { default ->
                        default.copy(
                            pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: default.pageSize,
                            startTimestamp = call.request.queryParameters["startTimestamp"]?.toLongOrNull()
                                ?: default.startTimestamp,
                            newer = call.request.queryParameters["newer"]?.toBooleanStrictOrNull() ?: default.newer,
                            includeScResults = call.request.queryParameters["includeScResults"]?.toBooleanStrictOrNull()
                                ?: default.includeScResults,
                            processTransactions = call.request.queryParameters["processTransactions"]?.toBooleanStrictOrNull()
                                ?: default.processTransactions
                        )
                    }

                    withContext(Dispatchers.IO) {
                        call.respond(TransactionRepository.getTransactions(request))
                    }
                } else {
                    call.response.status(HttpStatusCode.BadRequest)
                }
            }

            get("/transaction/{hash}") {
                startCallTrace()

                val hash = call.parameters["hash"]
                val process = call.request.queryParameters["process"]?.toBooleanStrictOrNull() ?: false
                if (hash.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.BadRequest)
                } else {
                    TransactionRepository.getTransaction(hash, process)?.let { transaction ->
                        if (process) {
                            withContext(Dispatchers.Default) {
                                startCustomTrace("ProcessSingleTransaction:$hash")
                                call.respond(
                                    TransactionProcessor.process(transaction.sender, transaction).also {
                                        endCustomTrace("ProcessSingleTransaction:$hash")
                                    }
                                )
                            }
                        } else {
                            call.respond(transaction)
                        }
                    } ?: call.response.status(HttpStatusCode.BadRequest)
                }

                endCallTrace()
            }
        }

        if (config.hasElrondConfig) {
            post("/transaction") {
                val newTransaction = call.receive<NewTransaction>()
                call.respond(TransactionRepository.sendTransaction(newTransaction))
            }

            get("/address/{address}") {
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

            get("/address/{address}/balance") {
                val address = call.parameters["address"]
                if (address.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.BadRequest)
                } else {
                    withContext(Dispatchers.IO) {
                        call.respond(AddressRepository.getAddressBalance(address))
                    }
                }
            }

            get("/address/{address}/nonce") {
                val address = call.parameters["address"]
                if (address.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.BadRequest)
                } else {
                    withContext(Dispatchers.IO) {
                        call.respond(AddressRepository.getAddressNonce(address))
                    }
                }
            }

            get("/network/config") {
                call.respond(NetworkConfig.cached())
            }

            get("/network/status") {
                call.respond(NetworkStatus.cached())
            }

            if (config.hasElastic) {
                get("/nodes") {
                    withContext(Dispatchers.IO) {
                        call.respond(Nodes.cached().value)
                    }
                }

                get("/stakingProviders") {
                    withContext(Dispatchers.IO) {
                        call.respond(StakingProviders.cached().value)
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
}
