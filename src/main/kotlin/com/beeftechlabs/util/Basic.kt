package com.beeftechlabs.util

fun <T> tryOrDefault(default: T, block: () -> T): T =
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