package com.beeftechlabs.model

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
