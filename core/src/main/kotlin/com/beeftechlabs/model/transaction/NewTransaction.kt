package com.beeftechlabs.model.transaction

import com.typesafe.config.Optional
import kotlinx.serialization.Serializable

@Serializable
data class NewTransaction(
    @Optional val chainId: String? = null,
    @Optional val chainID: String? = null,
    val data: String,
    val gasLimit: Long,
    val gasPrice: Long,
    val nonce: Long,
    val receiver: String,
    val sender: String,
    val signature: String,
    val value: String,
    val version: Int
)

@Serializable
data class NewTransactionState(
    val hash: String?,
    val receiverShard: Long,
    val senderShard: Long,
    val status: NewTransactionStatus,
    val error: String?
)

@Serializable
enum class NewTransactionStatus { Success, Pending, Failed }