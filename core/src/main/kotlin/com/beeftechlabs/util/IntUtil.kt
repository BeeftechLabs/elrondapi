package com.beeftechlabs.util

fun Int.isOdd(): Boolean = this and 0x01 != 0

fun Int.isEven(): Boolean = this and 0x01 == 0