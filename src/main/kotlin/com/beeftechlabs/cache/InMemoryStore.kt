package com.beeftechlabs.cache

import io.ktor.util.date.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object InMemoryStore : Store {

    private val store = mutableMapOf<String, Pair<Any, Long>>()

    override fun <T> get(key: String, ttl: Duration): T? {
        return store[key]?.let { (data, storedAt) ->
            if ((getTimeMillis() - storedAt).toDuration(DurationUnit.MILLISECONDS) > ttl) {
                null
            } else {
                data as? T
            }
        }
    }

    override fun <T> peek(key: String, ttl: Duration): T? {
        return store[key] as? T
    }

    override fun <T> set(key: String, data: T?) {
        store[key] = data as Any to getTimeMillis()
    }
}