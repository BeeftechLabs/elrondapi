package com.beeftechlabs.cache

import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun KClass<out Any>.key() = jvmName

inline fun <reified T> tryCache(key: String, ttl: Duration): T? {
    return InMemoryStore.get(key, ttl)
}

inline fun <reified T> peekCache(type: CacheType): T? {
    val key = T::class.jvmName
    return InMemoryStore.peek(key, type.ttl)
}

inline fun <reified T> putInCache(key: String, data: T?) = InMemoryStore.set(key, data)

inline fun <reified T> withCache(type: CacheType, instance: String = "", producer: () -> T): T {
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

interface Store {

    fun <T> get(key: String, ttl: Duration): T?

    fun <T> peek(key: String, ttl: Duration): T?

    fun <T> set(key: String, data: T?)
}
