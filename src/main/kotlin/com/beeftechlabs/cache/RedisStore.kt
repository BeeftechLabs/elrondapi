package com.beeftechlabs.cache

import com.beeftechlabs.config
import io.ktor.util.date.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPooled
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object RedisStore {

    var jedis: JedisPooled? = null

    val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun initialize() {
        config.redis?.let { redisConfig ->
            jedis = JedisPooled(redisConfig.url)
        }
    }

    inline fun <reified T> get(key: String, ttl: Duration): T? {
        return jedis?.let { jedis ->
            jedis.get(key)?.let { jsonValue ->
                val (value, storedAt) = json.decodeFromString<JedisWrapper<T>>(jsonValue)
                if ((getTimeMillis() - storedAt).toDuration(DurationUnit.MILLISECONDS) > ttl) {
                    jedis.set(key, null)
                    null
                } else {
                    value as? T
                }
            }
        }
    }

    fun <T> peek(key: String, ttl: Duration): T? {
        return jedis?.let { jedis ->
            jedis.get(key)?.let { jsonValue ->
                val (value, _) = json.decodeFromString<JedisWrapper<out Any>>(jsonValue)
                value as? T
            }
        }
    }

    inline fun <reified T> set(key: String, data: T?) {
        jedis?.set(key, json.encodeToString(JedisWrapper(data, getTimeMillis())))
    }

    @Serializable
    data class JedisWrapper<T>(
        @Contextual val value: T?,
        val storedAt: Long
    )
}