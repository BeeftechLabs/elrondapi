package com.beeftechlabs.model.transaction

import kotlinx.serialization.Serializable

@Serializable
data class TransactionsResponse(
    val hasMore: Boolean,
    val lastTimestamp: Long,
    val transactions: List<Transaction>
)
