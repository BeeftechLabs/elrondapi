package com.beeftechlabs.util

import com.soywiz.krypto.encoding.fromBase64
import com.soywiz.krypto.encoding.fromHex
import com.soywiz.krypto.encoding.hexLower

fun String.fromHexString(): String = fromHex().decodeToString()

fun String.toHexString(): String = encodeToByteArray().hexLower

fun String.fromBase64String(): String = fromBase64().decodeToString()

fun String.fromBase64ToHexString(): String = fromBase64().hexLower

fun String.tokenFromArg() = fromHexString().split("-").first()