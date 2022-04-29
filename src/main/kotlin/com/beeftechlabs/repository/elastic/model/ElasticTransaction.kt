package com.beeftechlabs.repository.elastic.model

import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.Transaction
import com.beeftechlabs.model.transaction.TransactionType
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

suspend fun ElasticTransaction.toTransaction(hash: String) = Transaction(
    hash = hash,
    sender = sender,
    receiver = receiver,
    transactionValue = Value.extract(value, "EGLD") ?: Value.zeroEgld(),
    outValues = emptyList(),
    inValues = emptyList(),
    outValuesRaw = emptyList(),
    inValuesRaw = emptyList(),
    data = data ?: "",
    nonce = nonce,
    gasLimit = gasLimit,
    gasPrice = gasPrice,
    gasUsed = gasUsed,
    fee = Value.extract(fee, "EGLD") ?: Value.zeroEgld(),
    timestamp = timestamp,
    senderShard = senderShard,
    receiverShard = receiverShard,
    tokens = tokens ?: emptyList(),
    esdtValues = esdtValues ?: emptyList(),
    status = status,
    type = TransactionType.Unknown,
    function = "",
    hasScResults = hasScResults,
    isScCall = isScCall,
    scResults = emptyList()
)
