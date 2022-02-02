package com.beeftechlabs.util

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger

private val denomination = BigDecimal.parseString("1000000000000000000")

fun String.denominatedBigDecimal(isHex: Boolean = true): BigDecimal =
    BigDecimal.fromBigInteger(BigInteger.parseString(this, if (isHex) 16 else 10))
        .divide(denomination)

fun String.bigDecimal(isHex: Boolean = true): BigDecimal =
    BigDecimal.fromBigInteger(BigInteger.parseString(this, if (isHex) 16 else 10))

fun String.bigInteger(isHex: Boolean = true): BigInteger =
    BigInteger.parseString(this, if (isHex) 16 else 10)

fun BigDecimal.denominated(): BigDecimal =
    divide(denomination)

fun BigInteger.denominated(): BigDecimal =
    BigDecimal.fromBigInteger(this).divide(denomination)

fun BigDecimal.formatted(roundPosition: Int = 5): String =
    roundToDigitPositionAfterDecimalPoint(roundPosition.toLong(), RoundingMode.FLOOR).toStringExpanded()

fun BigDecimal.toDouble(): Double = doubleValue(false)

fun BigDecimal.toLong(): Long = toBigInteger().longValue()

fun BigDecimal.nominated() = multiply(denomination)
