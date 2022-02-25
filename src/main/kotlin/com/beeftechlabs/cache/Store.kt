package com.beeftechlabs.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
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

suspend inline fun <reified T> putInCache(type: CacheType, data: T?, instance: String = "") {
    val key = if (instance.isNotEmpty()) "${type.name}:$instance" else type.name
    putInCache(key, data, type.ttl)
}

suspend inline fun <reified T> putInCache(key: String, data: T?, ttl: Duration) = coroutineScope {
    launch(Dispatchers.IO) { RedisStore.set(key, data, ttl) }
    InMemoryStore.set(key, data)
}

suspend inline fun <reified T> withCache(type: CacheType, instance: String = "", producer: () -> T): T {
    if (type.isAtomic) {
        type.mutex.lock()
    }
    val key = if (instance.isNotEmpty()) "${type.name}:$instance" else type.name
    val value = tryCache(key, type.ttl) ?: producer().also { putInCache(key, it, type.ttl) }
    if (type.isAtomic) {
        type.mutex.unlock()
    }
    return value
}

enum class CacheType(val ttl: Duration, val isAtomic: Boolean) {
    Esdts(1.hours, true),
    StakingProviders(1.hours, true),
    Nodes(1.hours, true),
    NetworkConfig(5.minutes, true),
    NetworkStatus(5.minutes, true),
    AddressDelegations(30.seconds, false),
    AddressDelegationsVm(30.seconds, false),
    AddressUndelegations(30.seconds, false),
    AddressClaimable(30.seconds, false),
    AddressTotalRewards(24.hours, false),
    Nfts(1.hours, true),
    Sfts(1.hours, true),
    TokenPairs(1.hours, true),
    TokenPairDetails(5.minutes, false),
    TokenAssets(10.days, true);

    val mutex = Mutex()
}
