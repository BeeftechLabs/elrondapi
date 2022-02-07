package com.beeftechlabs.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

inline fun <reified T> tryCache(key: String, ttl: Duration): T? {
    return InMemoryStore.get(key, ttl)
        ?: RedisStore.get<T?>(key)?.also { InMemoryStore.set(key, it) }
}

inline fun <reified T> peekCache(type: CacheType): T? {
    return InMemoryStore.peek(type.name) ?: RedisStore.peek(type.name)
}

suspend inline fun <reified T> putInCache(key: String, data: T?, ttl: Duration) = coroutineScope {
    launch(Dispatchers.IO) { RedisStore.set(key, data, ttl) }
    InMemoryStore.set(key, data)
}

suspend inline fun <reified T> withCache(type: CacheType, instance: String = "", producer: () -> T): T {
    val key = if (instance.isNotEmpty()) "${type.name}:$instance" else type.name

    return tryCache(key, type.ttl) ?: producer().also { putInCache(key, it, type.ttl) }
}

enum class CacheType(val ttl: Duration) {
    Tokens(24.hours),
    StakingProviders(24.hours),
    Nodes(1.hours),
    NetworkConfig(5.minutes),
    NetworkStatus(5.minutes),
    AddressDelegations(30.seconds),
    AddressDelegationsVm(30.seconds),
    AddressUndelegations(30.seconds),
    AddressClaimable(30.seconds),
    AddressTotalRewards(24.hours),
    Nfts(24.hours),
    Sfts(24.hours),
    TokenPairs(24.hours),
    TokenPairDetails(5.minutes)
}
