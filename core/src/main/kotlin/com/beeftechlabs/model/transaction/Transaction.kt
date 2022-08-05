package com.beeftechlabs.model.transaction

import com.beeftechlabs.model.token.Value
import kotlinx.serialization.Serializable

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
    val decodedData: String,
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
