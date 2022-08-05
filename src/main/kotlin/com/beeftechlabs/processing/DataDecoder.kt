package com.beeftechlabs.processing

import com.beeftechlabs.model.address.Address
import com.beeftechlabs.util.fromHexString
import com.beeftechlabs.util.toHexString
import com.ionspin.kotlin.bignum.integer.toBigInteger

object DataDecoder {

    fun decode(data: String): String {
        val decodedData = mutableListOf<String>()
        if (data.isNotEmpty()) {
            val terms = data.split("@")
            decodedData += terms[0]
            when (terms[0]) {
                "ESDTTransfer" -> decodedData += decodeEsdtTransfer(terms)
            }
        }
        return decodedData.joinToString(separator = "@")
    }

    private fun decodeEsdtTransfer(terms: List<String>): List<String> {
        val token = terms[1].fromHexString()
        val value = terms[2].toBigInteger(16).toString()
        val function = terms[3].fromHexString()
        val additional = mutableListOf<String>()

        terms.drop(3).forEach { term ->
            if (term.length == 64) {
                // Probably an address
                try {
                    additional.add(Address(term).erd)
                } catch (ignored: Exception) {
                    additional.add(term.fromHexString())
                }
            } else {
                val string = term.fromHexString()
                val parsed = if (string.any { !it.isLetter() && it != ' ' }) {
                    term.toBigIntegerOrNull(16)?.toString() ?: string
                } else {
                    string
                }
                additional.add(parsed)
            }
        }

        return listOf(token, value, function) + additional
    }
}