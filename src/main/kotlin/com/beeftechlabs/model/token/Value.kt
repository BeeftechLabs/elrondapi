package com.beeftechlabs.model.token

import com.beeftechlabs.util.denominated
import com.beeftechlabs.util.denominatedBigDecimal
import com.beeftechlabs.util.toDouble
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.serialization.Serializable

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

    companion object {
        val None = Value("", null, "")

        fun zero(token: String) = Value("0", 0.0, token)

        fun zeroEgld() = zero("EGLD")

        fun extractHex(bigNumber: String, token: String) = Value(
            bigNumber = bigNumber.toBigInteger(16).toString(),
            denominated = bigNumber.denominatedBigDecimal().toDouble(),
            token = token
        )

        fun extract(bigNumber: String, token: String) = Value(
            bigNumber = bigNumber,
            denominated = bigNumber.denominatedBigDecimal(isHex = false).toDouble(),
            token = token
        )

        fun extract(bigNumber: BigInteger, token: String) = Value(
            bigNumber = bigNumber.toString(),
            denominated = bigNumber.denominated().toStringExpanded().toDouble(),
            token = token
        )
    }
}