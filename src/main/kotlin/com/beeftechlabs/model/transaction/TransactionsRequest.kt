package com.beeftechlabs.model.transaction

import kotlinx.serialization.Serializable

@Serializable
data class TransactionsRequest(
    val address: String = "",
    val pageSize: Int = 20,
    val startTimestamp: Long = 0,
    val newer: Boolean = true,
    val includeScResults: Boolean = false,
    val processTransactions: Boolean = false
)