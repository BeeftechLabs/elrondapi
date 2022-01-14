package com.beeftechlabs.model.transaction

import com.beeftechlabs.model.elastic.ElasticScResult
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
    hash, sender, receiver, value, data ?: "", nonce, gasLimit, gasPrice, timestamp, prevTxHash, originalTxHash,
    hasOperations, tokens ?: emptyList(), esdtValues ?: emptyList()
)
