package com.beeftechlabs.model.transaction

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
