package com.beeftechlabs.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

inline fun <reified T> tryCache(key: String, ttl: Duration): T? {
    return InMemoryStore.get(key, ttl)
        ?: RedisStore.get<T?>(key, ttl)?.also { InMemoryStore.set(key, it) }
}

inline fun <reified T> peekCache(type: CacheType): T? {
    return InMemoryStore.peek(type.name, type.ttl) ?: RedisStore.peek(type.name, type.ttl)
}

suspend inline fun <reified T> putInCache(key: String, data: T?) = coroutineScope {
    launch(Dispatchers.IO) { RedisStore.set(key, data) }
    InMemoryStore.set(key, data)
}

suspend inline fun <reified T> withCache(type: CacheType, instance: String = "", producer: () -> T): T {
    val key = if (instance.isNotEmpty()) "${type.name}:$instance" else type.name

    return tryCache(key, type.ttl) ?: producer().also { putInCache(key, it) }
}

enum class CacheType(val ttl: Duration) {
    Tokens(24.hours),
    StakingProviders(24.hours),
    Nodes(1.hours),
    NetworkConfig(5.minutes),
    NetworkStatus(5.minutes),
    AddressDelegations(5.minutes),
    AddressUndelegations(5.minutes)
}
