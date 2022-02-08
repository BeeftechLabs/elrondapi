package com.beeftechlabs.cache

import com.beeftechlabs.config
import io.ktor.util.date.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object InMemoryStore {

    private val store = mutableMapOf<String, Pair<Any, Long>?>()

    fun <T> get(key: String, ttl: Duration): T? {
        return if (config.memoryStore) {
            store[key]?.let { (data, storedAt) ->
                if ((getTimeMillis() - storedAt).toDuration(DurationUnit.MILLISECONDS) > ttl) {
                    store[key] = null
                    null
                } else {
                    data as? T
                }
            }
        } else {
            null
        }
    }

    fun <T> peek(key: String): T? {
        return if (config.memoryStore) store[key] as? T else null
    }

    fun <T> set(key: String, data: T?) {
        if (config.memoryStore) {
            store[key] = data as Any to getTimeMillis()
        }
    }
}