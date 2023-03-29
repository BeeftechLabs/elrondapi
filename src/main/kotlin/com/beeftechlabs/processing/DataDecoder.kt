package com.beeftechlabs.processing

import com.beeftechlabs.model.address.Address
import com.beeftechlabs.util.fromHexString
import com.ionspin.kotlin.bignum.integer.toBigInteger

object DataDecoder {

    fun decode(data: String): String? {
        try {
            val decodedData = mutableListOf<String>()
            if (data.isNotEmpty()) {
                val terms = data.split("@")
                decodedData += terms[0]
                when (terms[0]) {
                    "ESDTTransfer" -> decodedData += decodeEsdtTransfer(terms)
                    "ESDTNFTTransfer" -> decodedData += decodeEsdtNftTransfer(terms)
                }
            }
            return decodedData.joinToString(separator = "@")
        } catch (exception: Exception) {
            println("Exception decoding data $data")
            println(exception)
            return null
        }
    }

    private fun decodeEsdtTransfer(terms: List<String>): List<String> {
        val token = terms[1].fromHexString()
        val value = terms[2].toBigInteger(16).toString()
        val function = terms.getOrNull(3)?.fromHexString()
        val additional = mutableListOf<String>()

        terms.drop(4).forEach { term ->
            if (term.length == 64) {
                // Probably an address
                try {
                    additional.add(Address(term).erd)
                } catch (ignored: Exception) {
                    additional.add(term.fromHexString())
                }
            } else {
                val string = term.fromHexString()
                val parsed = if (string.wasProbablyString()) {
                    string
                } else {
                    term.toBigIntegerOrNull(16)?.toString() ?: string
                }
                additional.add(parsed)
            }
        }

        return listOfNotNull(token, value, function) + additional
    }

    private fun decodeEsdtNftTransfer(terms: List<String>): List<String> {
        val token = terms[1].fromHexString()
        val nonce = terms[2].toInt(16).toString()
        val value = terms[3].toBigInteger(16).toString()
        val optAddress = terms.getOrNull(4)?.let { Address(it).erd }
        val function = terms.getOrNull(5)?.fromHexString()
        val additional = mutableListOf<String>()

        terms.drop(6).forEach { term ->
            if (term.length == 64) {
                // Probably an address
                try {
                    additional.add(Address(term).erd)
                } catch (ignored: Exception) {
                    println(ignored)
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

        return listOfNotNull(token, nonce, value, optAddress, function) + additional
    }

    private fun String.wasProbablyString() =
        !any { !it.isLetter() && !it.isDigit() && !ALLOWED_STRING_SYM.contains(it) }

    private val ALLOWED_STRING_SYM = listOf(' ', '-')
}