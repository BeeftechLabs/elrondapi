package com.beeftechlabs.util

import com.ionspin.kotlin.bignum.integer.toBigInteger
import com.soywiz.krypto.encoding.fromBase64
import com.soywiz.krypto.encoding.fromHex
import com.soywiz.krypto.encoding.hexLower

fun String.fromHexString(): String =
    try {
        trim().fromHex().decodeToString()
    } catch (ex: Exception) {
        println("Error decoding hex string: $this")
        throw ex
    }

fun String.toHexString(): String = encodeToByteArray().hexLower

fun String.fromBase64String(): String = fromBase64().decodeToString()

fun String.fromBase64ToHexString(): String = fromBase64().hexLower

fun String.tokenFromIdentifier() = split("-").first()

fun String.vmQueryToInt(): Int = fromBase64ToHexString().toBigInteger(16).intValue()

fun String.vmQueryToLong(): Long = fromBase64ToHexString().toBigInteger(16).longValue()