package com.beeftechlabs.util

fun <T> tryOrDefault(default: T, block: () -> T): T =
    try {
        block()
    } catch (_: Exception) {
        default
    }

suspend fun <T> tryCoroutineOrDefault(default: T, block: suspend () -> T): T =
    try {
        block()
    } catch (_: Exception) {
        default
    }

fun <T> tryOrNull(block: () -> T): T? =
    try {
        block()
    } catch (_: Exception) {
        null
    }