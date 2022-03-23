package com.beeftechlabs.model.transaction

import com.beeftechlabs.model.token.Value
import com.beeftechlabs.repository.elastic.model.ElasticTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Transaction(
    val hash: String,
    val sender: String,
    val receiver: String,
    val transactionValue: Value,
    val outValues: List<Value>,
    val inValues: List<Value>,
    val outValuesRaw: List<Value>,
    val inValuesRaw: List<Value>,
    val data: String,
    val nonce: Long,
    val gasLimit: Long,
    val gasPrice: Long,
    val gasUsed: Long,
    val fee: Value,
    val timestamp: Long,
    val senderShard: Long,
    val receiverShard: Long,
    val tokens: List<String>,
    val esdtValues: List<String>,
    val status: String,
    val type: TransactionType,
    val function: String?,
    val hasScResults: Boolean,
    val isScCall: Boolean,
    val scResults: List<ScResult>
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