package com.beeftechlabs.repository.transaction

import kotlinx.serialization.Serializable

@Serializable
data class PostTransactionResponse(
    val data: PostTransactionData?,
    val error: String? = null
)

@Serializable
data class PostTransactionData(
    val txHash: String? = null,
    val error: String? = null
)
