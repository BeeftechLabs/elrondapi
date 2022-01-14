package com.beeftechlabs.cache

import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration

fun KClass<out Any>.key() = jvmName

val storeTtls = mutableMapOf<String, Duration>()
val storeInitializers = mutableMapOf<String, suspend () -> Any>()

suspend inline fun <reified T> getFromStore(): T {
    val key = T::class.jvmName
    return InMemoryStore.get<T>(key, storeTtls[key]!!).let { value ->
        value ?: storeInitializers[key]!!.invoke().also { setInStore(it) }
    } as T
}

inline fun <reified T> getFromStoreStrictly(): T? {
    val key = T::class.jvmName
    return InMemoryStore.get(key, storeTtls[key]!!)
}

inline fun <reified T> peekStore(): T? {
    val key = T::class.jvmName
    return InMemoryStore.peek(key, storeTtls[key]!!)
}

inline fun <reified T> setInStore(data: T?) = InMemoryStore.set(T::class.java.name, data)

interface Store {

    fun <T> get(key: String, ttl: Duration): T?

    fun <T> peek(key: String, ttl: Duration): T?

    fun <T> set(key: String, data: T?)
}
