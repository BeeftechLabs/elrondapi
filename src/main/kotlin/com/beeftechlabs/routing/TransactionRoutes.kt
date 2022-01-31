package com.beeftechlabs.routing

import com.beeftechlabs.config
import com.beeftechlabs.model.transaction.NewTransaction
import com.beeftechlabs.model.transaction.TransactionsRequest
import com.beeftechlabs.plugins.endCallTrace
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCallTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.processing.TransactionProcessor
import com.beeftechlabs.repository.transaction.TransactionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Routing.transactionRoutes() {
    if (config.hasElastic) {
        post("/transactions") {
            val request = call.receive<TransactionsRequest>()

            withContext(Dispatchers.IO) {
                call.respond(TransactionRepository.getTransactions(request))
            }
        }

        get("/transactions") {
            val request = TransactionsRequest().let { default ->
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
                                TransactionProcessor.process(transaction).also {
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
    }
}