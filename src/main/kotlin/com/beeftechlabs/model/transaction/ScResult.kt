package com.beeftechlabs.model.transaction

import com.beeftechlabs.repository.elastic.model.ElasticScResult
import kotlinx.serialization.Serializable

@Serializable
data class ScResult(
    val hash: String,
    val sender: String,
    val receiver: String,
    val value: String,
    val data: String,
    val nonce: Long,
    val gasLimit: Long,
    val gasPrice: Long,
    val timestamp: Long,
    val prevTxHash: String,
    val originalTxHash: String,
    val hasOperations: Boolean,
    val tokens: List<String>,
    val esdtValues: List<String>
)

fun ElasticScResult.toScResult(hash: String) = ScResult(
    hash = hash,
    sender = sender,
    receiver = receiver,
    value = value,
    data = data ?: "",
    nonce = nonce,
    gasLimit = gasLimit,
    gasPrice = gasPrice,
    timestamp = timestamp,
    prevTxHash = prevTxHash,
    originalTxHash = originalTxHash,
    hasOperations = hasOperations,
    tokens = tokens ?: emptyList(),
    esdtValues = esdtValues ?: emptyList()
)
