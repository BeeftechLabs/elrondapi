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
import com.beeftechlabs.repository.address.AddressRepository
import com.beeftechlabs.repository.transaction.TransactionRepository
import com.beeftechlabs.routing.addressRoutes
import com.beeftechlabs.routing.coreNetworkRoutes
import com.beeftechlabs.routing.tokenRoutes
import com.beeftechlabs.routing.transactionRoutes
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
            resource("/favicon.ico", "favicon.ico")
            resource("/openapi.yaml", "openapi.yaml")
        }

        coreNetworkRoutes()
        addressRoutes()
        transactionRoutes()
        tokenRoutes()
    }
}
