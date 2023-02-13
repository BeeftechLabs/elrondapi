package com.beeftechlabs.cache

import com.beeftechlabs.config
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.params.SetParams
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object RedisStore {

    val jedisPool: JedisPool? by lazy {
        if (config.redis?.enabled == true) {
            JedisPool(
                JedisPoolConfig().apply {
                    maxTotal = 128
                },
                config.redis.host,
                config.redis.port
            )
        } else {
            null
        }
    }

    val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    inline fun <reified T> get(key: String): T? {
        return jedisPool?.resource?.use { jedis ->
            try {
                jedis.get(key)?.let { jsonValue ->
                    json.decodeFromString<T>(jsonValue)
                }
            } catch (exception: Exception) {
                logger.error(exception) { "Error getting $key" }
                null
            }
        }
    }

    inline fun <reified T> peek(key: String): T? {
        return jedisPool?.resource?.use { jedis ->
            try {
                jedis.get(key)?.let { jsonValue ->
                    json.decodeFromString<T>(jsonValue)
                }
            } catch (exception: Exception) {
                logger.error(exception) { "Error getting $key" }
                null
            }
        }
    }

    inline fun <reified T> set(key: String, data: T?, ttl: Duration) {
        jedisPool?.resource?.use { jedis ->
            try {
                jedis.set(
                    key,
                    json.encodeToString(data),
                    SetParams.setParams().ex(ttl.inWholeSeconds)
                )
            } catch (exception: Exception) {
                logger.error(exception) { "Error setting $key" }
            }
        }
    }

    fun getTtl(key: String, cacheType: CacheType): Duration {
        return jedisPool?.resource?.use { jedis ->
            jedis.ttl(key).takeIf { it > 0 }?.seconds
        } ?: cacheType.ttl
    }

    fun clear() {
        jedisPool?.resource?.use { it.flushAll() }
    }

    val logger = KotlinLogging.logger {}
}