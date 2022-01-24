package com.beeftechlabs.cache

import com.beeftechlabs.config
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.params.SetParams
import kotlin.time.Duration

object RedisStore {

    val jedis: JedisPooled? by lazy { if (config.redis?.enabled == true) JedisPooled(config.redis.url) else null }

    val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    inline fun <reified T> get(key: String): T? {
        return jedis?.let { jedis ->
            jedis.get(key)?.let { jsonValue ->
                try {
                    json.decodeFromString<T>(jsonValue)
                }
                catch (exception: Exception) {
                    println("Error reading $key: $exception")
                    println("Had in store: $jsonValue")
                    null
                }
            }
        }
    }

    inline fun <reified T> peek(key: String): T? {
        return jedis?.let { jedis ->
            jedis.get(key)?.let { jsonValue ->
                try {
                    json.decodeFromString<T>(jsonValue)
                }
                catch (exception: Exception) {
                    println("Error reading $key: $exception")
                    println("Had in store: $jsonValue")
                    null
                }
            }
        }
    }

    inline fun <reified T> set(key: String, data: T?, ttl: Duration) {
        jedis?.set(
            key,
            json.encodeToString(data),
            SetParams.setParams().ex(ttl.inWholeSeconds)
        )
    }
}