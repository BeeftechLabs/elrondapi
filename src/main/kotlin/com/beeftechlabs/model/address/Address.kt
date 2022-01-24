package com.beeftechlabs.model.address

import com.beeftechlabs.util.Bech32
import com.beeftechlabs.util.convertBits
import com.soywiz.krypto.encoding.fromHex
import com.soywiz.krypto.encoding.hex
import com.soywiz.krypto.encoding.unhex
import kotlin.experimental.and

data class Address private constructor(
    val erd: String = "",
    val hex: String = ""
) {
    val shard: Long
        get() {
            val pubKey = hex.fromHex()
            val pubKeyPrefix = pubKey.take(25)
            if (pubKeyPrefix == METACHAIN_PREFIX || pubKeyPrefix == ZERO_PREFIX) {
                return META_SHARD
            }

            val shard = pubKey.last() and 3
            if (shard >= NUM_SHARDS) {
                return (pubKey.last() and 1).toLong()
            }
            return shard.toLong()
        }

    constructor(value: String) : this(erd = erdValue(value), hex = hexValue(value))

    fun isSmartContract(): Boolean {
        return false // todo
    }

    companion object {
        private const val HRP = "erd"
        private const val PUBKEY_LENGTH = 32
        private const val PUBKEY_STRING_LENGTH = PUBKEY_LENGTH * 2
        private const val BECH32_LENGTH = 62
        private const val NUM_SHARDS = 3
        private const val META_SHARD = 4294967295L
        private val METACHAIN_PREFIX =
            (generateSequence(0) { it }.take(9) + listOf(1) + generateSequence(0) { it }.take(15))
                .map { it.toByte() }
        private val ZERO_PREFIX = generateSequence(0) { it }.take(25)

        private fun erdValue(value: String) = if (value.length == PUBKEY_STRING_LENGTH) {
            Bech32.encode(HRP, value.unhex.convertBits(8, 5))
        } else {
            value
        }

        private fun hexValue(value: String) = if (value.length == PUBKEY_STRING_LENGTH) {
            value
        } else {
            Bech32.decode(value).data.convertBits(5, 8, false).hex
        }
    }
}

fun String.toAddress() = Address(this)
