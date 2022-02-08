package com.beeftechlabs.model.token

import com.beeftechlabs.util.denominated
import com.beeftechlabs.util.denominatedBigDecimal
import com.beeftechlabs.util.formatted
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
            extract(first.add(second), token)
        } else {
            this
        }
    }

    operator fun minus(other: Value): Value {
        return if (token == other.token) {
            val first = BigInteger.parseString(bigNumber)
            val second = BigInteger.parseString(other.bigNumber)
            extract(first.minus(second), token)
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

        fun zero(token: String) = Value("0", 0.0, token)

        fun zeroEgld() = zero("EGLD")

        fun extractHex(bigNumber: String, token: String, onError: (() -> Value)? = null) =
            try {
                Value(
                    bigNumber = bigNumber.toBigInteger(16).toString(),
                    denominated = bigNumber.denominatedBigDecimal().toDouble(),
                    token = token
                )
            } catch (exception: Exception) {
//                logger.error(exception) { "Error extracting value $bigNumber from hex string" }
                onError?.invoke() ?: None
            }

        fun extract(bigNumber: String, token: String, onError: (() -> Value)? = null) =
            try {
                Value(
                    bigNumber = bigNumber,
                    denominated = bigNumber.denominatedBigDecimal(isHex = false).toDouble(),
                    token = token
                )
            } catch (exception: Exception) {
//                logger.error(exception) { "Error extracting value $bigNumber from string" }
                onError?.invoke() ?: None
            }

        fun extract(bigNumber: BigInteger, token: String, onError: (() -> Value)? = null) =
            try {
                Value(
                    bigNumber = bigNumber.toString(),
                    denominated = bigNumber.denominated().toDouble(),
                    token = token
                )
            } catch (exception: Exception) {
//                logger.error(exception) { "Error extracting value $bigNumber from string" }
                onError?.invoke() ?: None
            }

        private val logger = KotlinLogging.logger {}
    }
}