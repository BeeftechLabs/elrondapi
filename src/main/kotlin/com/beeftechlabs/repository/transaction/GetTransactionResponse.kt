package com.beeftechlabs.repository.transaction

import kotlinx.serialization.Serializable

@Serializable
data class GetTransactionResponse(
    val data: GetTransactionData,
    val error: String?
)

@Serializable
data class GetTransactionData(
    val transaction: GetTransactionWrapper
)

@Serializable
data class GetTransactionWrapper(
    val status: String,
    val sourceShard: Long,
    val destinationShard: Long,
)