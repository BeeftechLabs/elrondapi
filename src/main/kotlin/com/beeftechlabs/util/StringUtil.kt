package com.beeftechlabs.util

import com.soywiz.krypto.encoding.fromBase64
import com.soywiz.krypto.encoding.fromHex

fun String.fromHexString(): String = fromHex().decodeToString()

fun String.fromBase64String(): String = fromBase64().decodeToString()

fun String.tokenFromArg() = fromHexString().split("-").first()