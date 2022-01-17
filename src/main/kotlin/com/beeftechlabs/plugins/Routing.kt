package com.beeftechlabs.plugins

import com.beeftechlabs.config
import com.beeftechlabs.model.transaction.TransactionsRequest
import com.beeftechlabs.processing.TransactionProcessor
import com.beeftechlabs.repository.ElasticRepository
import com.beeftechlabs.repository.Nodes
import com.beeftechlabs.repository.StakingProviders
import com.beeftechlabs.repository.TransactionRepository
import com.beeftechlabs.repository.token.AllTokens
import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.repository.token.address.AddressRepository
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
                try {
                    val request = call.receive<TransactionsRequest>()

                    withContext(Dispatchers.IO) {
                        call.respond(TransactionRepository.getTransactions(request))
                    }
                } catch (exception: Exception) {
                    call.response.status(HttpStatusCode.BadRequest)
                }
            }

            get("/transaction/{hash}") {
                val hash = call.parameters["hash"]
                val process = call.request.queryParameters["process"]?.toBooleanStrictOrNull() ?: true
                if (hash.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.BadRequest)
                } else {
                    TransactionRepository.getTransaction(hash, process)?.let { transaction ->
                        if (process) {
                            withContext(Dispatchers.Default) {
                                call.respond(TransactionProcessor.process(transaction.sender, transaction))
                            }
                        } else {
                            call.respond(transaction)
                        }
                    } ?: call.response.status(HttpStatusCode.BadRequest)
                }
            }
        }

        if (config.hasElrondConfig) {
            get("/address/{address}") {
                val address = call.parameters["address"]
                if (address.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.BadRequest)
                } else {
                    withContext(Dispatchers.IO) {
                        call.respond(AddressRepository.getAddressDetails(address))
                    }
                }
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
        }
    }
}
