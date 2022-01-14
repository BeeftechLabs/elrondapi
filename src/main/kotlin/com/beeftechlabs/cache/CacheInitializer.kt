package com.beeftechlabs.cache

import com.beeftechlabs.model.core.Nodes
import com.beeftechlabs.repository.*
import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.repository.token.AllTokens
import kotlin.time.Duration.Companion.hours

object CacheInitializer {

    fun initialize() {
        storeTtls[Nodes::class.key()] = 1.hours
        storeInitializers[Nodes::class.key()] = NodeRepository::getAllNodes

        storeTtls[StakingProviders::class.key()] = 24.hours
        storeInitializers[StakingProviders::class.key()] = StakingRepository::getDelegationProviders

        storeTtls[AllTokens::class.key()] = 24.hours
        storeInitializers[AllTokens::class.key()] = TokenRepository::getAllTokens
    }
}