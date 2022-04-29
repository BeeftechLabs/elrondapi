package com.beeftechlabs.util

fun <T, R> letAll(vararg values: T?, transform: (values: Array<T>) -> R): R? =
    if (values.any { it == null }) {
        null
    } else {
        transform(values as Array<T>)
    }