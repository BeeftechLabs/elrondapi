package com.beeftechlabs.model.transaction

import kotlinx.serialization.Serializable

@Serializable
data class TransactionsResponse(
    val transactions: List<Transaction>,
    val hasMore: Boolean,
    val lastTimestamp: Long
)
