package com.beeftechlabs.util

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import com.soywiz.krypto.encoding.fromBase64
import com.soywiz.krypto.encoding.fromHex
import com.soywiz.krypto.encoding.hexLower

fun String.fromHexString(): String = fromHex().decodeToString()

fun String.toHexString(): String = encodeToByteArray().hexLower

fun String.fromBase64String(): String = fromBase64().decodeToString()

fun String.fromBase64ToHexString(): String = fromBase64().hexLower

fun String.tokenFromArg() = fromHexString().split("-").first()

fun String.vmQueryToInt(): Int = fromBase64ToHexString().toBigInteger(16).intValue()

fun String.vmQueryToLong(): Long = fromBase64ToHexString().toBigInteger(16).longValue()