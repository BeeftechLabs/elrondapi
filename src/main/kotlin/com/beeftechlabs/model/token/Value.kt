package com.beeftechlabs.model.token

import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.util.denominatedBigDecimal
import com.beeftechlabs.util.toDouble
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.serialization.Serializable
import mu.KotlinLogging

@Serializable
data class Value(
    val bigNumber: String,
    val denominated: Double?,
    val token: String
) {
    operator fun plus(other: Value): Value {
        return if (token == other.token) {
            val first = BigInteger.parseString(bigNumber)
            val second = BigInteger.parseString(other.bigNumber)
            Value(first.add(second).toString(), denominated?.plus(other.denominated ?: 0.0), token)
        } else {
            this
        }
    }

    operator fun minus(other: Value): Value {
        return if (token == other.token) {
            val first = BigInteger.parseString(bigNumber)
            val second = BigInteger.parseString(other.bigNumber)
            Value(first.minus(second).toString(), denominated?.minus(other.denominated ?: 0.0), token)
        } else {
            this
        }
    }

    fun abs(): Value = bigNumber.toBigDecimal().abs().let { abs ->
        Value(
            abs.toString(),
            abs.toDouble(),
            token
        )
    }

    companion object {
        val None = Value("", null, "")

        private const val MAX_PARSING_LENGTH = 64

        fun zero(token: String) = Value("0", 0.0, token)

        fun zeroEgld() = zero("EGLD")

        suspend fun extractHex(bigNumber: String, tokenId: String, onError: (() -> Value)? = null) =
            if (bigNumber.isNotEmpty() && bigNumber.length < MAX_PARSING_LENGTH) {
                try {
                    val decimals = TokenRepository.getDecimalsForToken(tokenId)
                    val bigInteger = bigNumber.toBigInteger(16)
                    Value(
                        bigNumber = bigInteger.toString(),
                        denominated = bigInteger.denominatedBigDecimal(decimals = decimals).toDouble()
                            .takeIf { it.isFinite() },
                        token = tokenId
                    )
                } catch (exception: Exception) {
                    logger.error(exception) { "Error extracting value $bigNumber from hex string" }
                    onError?.invoke()
                }
            } else {
                onError?.invoke()
            }

        suspend fun extract(bigNumber: String, tokenId: String, onError: (() -> Value)? = null) =
            if (bigNumber.isNotEmpty()) {
                try {
                    val decimals = TokenRepository.getDecimalsForToken(tokenId)
                    Value(
                        bigNumber = bigNumber,
                        denominated = bigNumber.denominatedBigDecimal(isHex = false, decimals = decimals).toDouble()
                            .takeIf { it.isFinite() },
                        token = tokenId
                    )
                } catch (exception: Exception) {
                    logger.error(exception) { "Error extracting value $bigNumber from string" }
                    onError?.invoke()
                }
            } else {
                onError?.invoke()
            }

        private val logger = KotlinLogging.logger {}
    }
}