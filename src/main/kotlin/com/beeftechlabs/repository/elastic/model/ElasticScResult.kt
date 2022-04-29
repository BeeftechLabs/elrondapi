package com.beeftechlabs.repository.elastic.model

import com.beeftechlabs.model.transaction.ScResult
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticScResult(
    @JsonProperty("sender") val sender: String,
    @JsonProperty("receiver") val receiver: String,
    @JsonProperty("value") val value: String,
    @JsonProperty("data") val data: String?,
    @JsonProperty("nonce") val nonce: Long,
    @JsonProperty("gasLimit") val gasLimit: Long,
    @JsonProperty("gasPrice") val gasPrice: Long,
    @JsonProperty("timestamp") val timestamp: Long,
    @JsonProperty("prevTxHash") val prevTxHash: String,
    @JsonProperty("originalTxHash") val originalTxHash: String,
    @JsonProperty("hasOperations") val hasOperations: Boolean,
    @JsonProperty("tokens") val tokens: List<String>?,
    @JsonProperty("esdtValues") val esdtValues: List<String>?
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
