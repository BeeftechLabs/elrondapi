package com.beeftechlabs.model.token

import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.util.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import mu.KotlinLogging

@Serializable
data class Value(
    val bigNumber: String,
    val decimals: Int,
    val denominated: Double,
    val token: String
) {
    @Transient var big: BigInteger? = null

    operator fun plus(other: Value): Value {
        return if (token == other.token) {
            val first = BigInteger.parseString(bigNumber)
            val second = BigInteger.parseString(other.bigNumber)
            Value(first.add(second).toString(), decimals, denominated.plus(other.denominated), token)
        } else {
            this
        }
    }

    operator fun minus(other: Value): Value {
        return if (token == other.token) {
            val first = BigInteger.parseString(bigNumber)
            val second = BigInteger.parseString(other.bigNumber)
            Value(first.minus(second).toString(), decimals, denominated.minus(other.denominated), token)
        } else {
            this
        }
    }

    fun hexValue(): String = BigInteger.parseString(bigNumber).toString(16).ensureHexLength()

    fun bigValue(): BigInteger = big ?: bigNumber.toBigInteger()

    companion object {
        val EGLD = "EGLD"

        val None = Value("0", 0, 0.0, "")

        val ZeroEgld = zero(EGLD)

        private const val MAX_PARSING_LENGTH = 64

        fun zero(token: String) = Value("0", 0, 0.0, token)

        suspend fun extractHex(bigNumber: String, tokenId: String, onError: (() -> Value) = { zero(tokenId) }): Value =
            if (bigNumber.isNotEmpty() && bigNumber.length < MAX_PARSING_LENGTH) {
                try {
                    val decimals = TokenRepository.getDecimalsForToken(tokenId)
                    val bigInteger = bigNumber.toBigInteger(16)
                    val denominated = bigInteger.denominatedBigDecimal(decimals = decimals).toDouble()
                        .takeIf { it.isFinite() } ?: 0.0
                    Value(
                        bigNumber = bigInteger.toString(),
                        decimals = decimals,
                        denominated = denominated,
                        token = tokenId
                    ).also { it.big = bigInteger }
                } catch (exception: Exception) {
                    logger.error(exception) { "Error extracting value $bigNumber from hex string" }
                    onError()
                }
            } else {
                logger.error { "Error extracting value, big number is empty" }
                onError()
            }

        suspend fun extract(bigNumber: String?, tokenId: String, onError: (() -> Value) = { zero(tokenId) }) =
            if (!bigNumber.isNullOrEmpty()) {
                try {
                    val decimals = TokenRepository.getDecimalsForToken(tokenId)
                    val bigInteger = bigNumber.toBigInteger()
                    val denominated = bigInteger.denominated().toDouble().takeIf { it.isFinite() } ?: 0.0
                    Value(
                        bigNumber = bigNumber,
                        decimals = decimals,
                        denominated = denominated,
                        token = tokenId
                    ).also { it.big = bigInteger }
                } catch (exception: Exception) {
                    logger.error(exception) { "Error extracting value $bigNumber from string" }
                    onError()
                }
            } else {
                logger.error { "Error extracting value, big number is empty" }
                onError()
            }

        suspend fun extract(value: Double, tokenId: String, onError: (() -> Value)? = null) =
            try {
                val decimals = TokenRepository.getDecimalsForToken(tokenId)
                val bigInteger = BigDecimal.fromDouble(value).nominated(decimals).toBigInteger()
                val nominated = bigInteger.toString()
                Value(
                    bigNumber = nominated,
                    decimals = decimals,
                    denominated = value,
                    token = tokenId
                ).also { it.big = bigInteger }
            } catch (exception: Exception) {
                logger.error(exception) { "Error extracting value $value from Double" }
                onError?.invoke()
            }

        val logger = KotlinLogging.logger {}
    }
}

suspend fun BigInteger.toValue(tokenId: String, onError: (() -> Value) = { Value.zero(tokenId) }): Value {
    return try {
        val decimals = TokenRepository.getDecimalsForToken(tokenId)
        val denominated = denominatedBigDecimal(decimals = decimals).toDouble()
            .takeIf { it.isFinite() } ?: 0.0
        Value(
            bigNumber = toString(),
            decimals = decimals,
            denominated = denominated,
            token = tokenId
        ).also { it.big = this }
    } catch (exception: Exception) {
        Value.logger.error(exception) { "Error extracting value $this from hex string" }
        onError()
    }
}