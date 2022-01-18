package com.beeftechlabs.repository.elastic.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticTransaction(
    @JsonProperty("sender") val sender: String,
    @JsonProperty("receiver") val receiver: String,
    @JsonProperty("value") val value: String,
    @JsonProperty("data") val data: String? = null,
    @JsonProperty("nonce") val nonce: Long,
    @JsonProperty("gasLimit") val gasLimit: Long,
    @JsonProperty("gasPrice") val gasPrice: Long,
    @JsonProperty("gasUsed") val gasUsed: Long,
    @JsonProperty("fee") val fee: String,
    @JsonProperty("timestamp") val timestamp: Long,
    @JsonProperty("senderShard") val senderShard: Long,
    @JsonProperty("receiverShard") val receiverShard: Long,
    @JsonProperty("hasScResults") val hasScResults: Boolean,
    @JsonProperty("isScCall") val isScCall: Boolean,
    @JsonProperty("hasOperations") val hasOperations: Boolean,
    @JsonProperty("tokens") val tokens: List<String>? = null,
    @JsonProperty("esdtValues") val esdtValues: List<String>? = null,
    @JsonProperty("status") val status: String
)
