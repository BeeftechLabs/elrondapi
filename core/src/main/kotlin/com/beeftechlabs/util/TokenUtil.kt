package com.beeftechlabs.util

fun String.collectionId() = split("-").take(2).joinToString("-")
