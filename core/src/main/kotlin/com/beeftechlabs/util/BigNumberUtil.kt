package com.beeftechlabs.util

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger

fun String.denominatedBigDecimal(isHex: Boolean = true, decimals: Int = 18): BigDecimal =
    BigDecimal.fromBigInteger(BigInteger.parseString(this, if (isHex) 16 else 10))
        .divide(denomination(decimals))

fun BigInteger.denominatedBigDecimal(decimals: Int = 18): BigDecimal =
    BigDecimal.fromBigInteger(this).divide(denomination(decimals))

fun String.bigDecimal(isHex: Boolean = true): BigDecimal =
    BigDecimal.fromBigInteger(BigInteger.parseString(this, if (isHex) 16 else 10))

fun String.bigInteger(isHex: Boolean = true): BigInteger =
    BigInteger.parseString(this, if (isHex) 16 else 10)

fun BigDecimal.denominated(decimals: Int = 18): BigDecimal =
    divide(denomination(decimals))

fun BigInteger.denominated(decimals: Int = 18): BigDecimal =
    BigDecimal.fromBigInteger(this).divide(denomination(decimals))

fun BigDecimal.formatted(roundPosition: Int = 5): String =
    roundToDigitPositionAfterDecimalPoint(roundPosition.toLong(), RoundingMode.FLOOR).toStringExpanded()

// TODO: this doesn't work correctly in all cases apparently (10000000000 / 1000000 outputs wrong value)
//fun BigDecimal.toDouble(): Double = doubleValue(false)
fun BigDecimal.toDouble(): Double = formatted().toDouble()

fun BigDecimal.toLong(): Long = toBigInteger().longValue()

fun BigDecimal.nominated(decimals: Int = 18) = multiply(denomination(decimals))

private val denominations = mutableMapOf<Int, BigDecimal>().apply {
    put(1, BigDecimal.parseString("10"))
    put(18, BigDecimal.parseString("1000000000000000000"))
}

private fun denomination(decimals: Int): BigDecimal = denominations[decimals] ?: run {
    BigDecimal.TEN.pow(decimals).also { denominations[decimals] = it }
}

fun String.ensureHexLength() =
    if (length.isOdd()) "0$this" else this
