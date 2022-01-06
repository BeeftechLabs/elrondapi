package com.beeftechlabs.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionsResponse(
    val transactions: List<Transaction>,
    val hasMore: Boolean,
    val lastTimestamp: Long
)
