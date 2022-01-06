package com.beeftechlabs.plugins

import com.beeftechlabs.model.TransactionsRequest
import com.beeftechlabs.processing.TransactionProcessor
import com.beeftechlabs.repository.TransactionRepository
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
                        call.respond(TransactionProcessor.process(transaction.sender, transaction))
                    } else {
                        call.respond(transaction)
                    }
                } ?: call.response.status(HttpStatusCode.BadRequest)
            }
        }
    }
}
