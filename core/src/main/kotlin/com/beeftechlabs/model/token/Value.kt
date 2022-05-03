package com.beeftechlabs.model.token

import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.util.denominatedBigDecimal
import com.beeftechlabs.util.ensureHexLength
import com.beeftechlabs.util.nominated
import com.beeftechlabs.util.toDouble
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.serialization.Serializable
import mu.KotlinLogging

@Serializable
data class Value(
    val bigNumber: String,
    val decimals: Int,
    val denominated: Double,
    val token: String
) {
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

    companion object {
        val None = Value("0", 0, 0.0, "")

        val ZeroEgld = zero("EGLD")

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
                    )
                } catch (exception: Exception) {
                    logger.error(exception) { "Error extracting value $bigNumber from hex string" }
                    onError()
                }
            } else {
                logger.error { "Error extracting value, big number is empty" }
                onError()
            }

        suspend fun extract(bigNumber: String, tokenId: String, onError: (() -> Value) = { zero(tokenId) }) =
            if (bigNumber.isNotEmpty()) {
                try {
                    val decimals = TokenRepository.getDecimalsForToken(tokenId)
                    val denominated = bigNumber.denominatedBigDecimal(isHex = false, decimals = decimals).toDouble()
                        .takeIf { it.isFinite() } ?: 0.0
                    Value(
                        bigNumber = bigNumber,
                        decimals = decimals,
                        denominated = denominated,
                        token = tokenId
                    )
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
                val nominated = BigDecimal.fromDouble(value).nominated(decimals).toBigInteger().toString()
                Value(
                    bigNumber = nominated,
                    decimals = decimals,
                    denominated = value,
                    token = tokenId
                )
            } catch (exception: Exception) {
                logger.error(exception) { "Error extracting value $value from Double" }
                onError?.invoke()
            }

        private val logger = KotlinLogging.logger {}
    }
}