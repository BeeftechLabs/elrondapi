package com.beeftechlabs.model.address

import com.beeftechlabs.util.Bech32
import com.beeftechlabs.util.convertBits
import com.soywiz.krypto.encoding.hex
import com.soywiz.krypto.encoding.unhex

data class Address private constructor(
    val erd: String = "",
    val hex: String = ""
) {
    constructor(value: String) : this(erd = erdValue(value), hex = hexValue(value))

    fun isSmartContract(): Boolean {
        return false // todo
    }

    companion object {
        private const val HRP = "erd"
        private const val PUBKEY_LENGTH = 32
        private const val PUBKEY_STRING_LENGTH = PUBKEY_LENGTH * 2
        private const val BECH32_LENGTH = 62

        private fun erdValue(value: String) = if (value.length == PUBKEY_STRING_LENGTH) {
            Bech32.encode(HRP, value.unhex.convertBits(8, 5))
        } else {
            value
        }

        private fun hexValue(value: String) = if (value.length == PUBKEY_STRING_LENGTH) {
            value
        } else {
            Bech32.decode(value).data.convertBits(5, 8, false).hex
        }
    }
}