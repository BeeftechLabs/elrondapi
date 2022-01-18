package com.beeftechlabs.repository

import com.beeftechlabs.model.transaction.Transaction
import com.beeftechlabs.model.transaction.TransactionsRequest
import com.beeftechlabs.model.transaction.TransactionsResponse
import com.beeftechlabs.repository.elastic.ElasticRepository

object TransactionRepository {

    suspend fun getTransaction(hash: String, process: Boolean): Transaction? =
        ElasticRepository.getTransaction(hash, process)

    suspend fun getTransactions(request: TransactionsRequest): TransactionsResponse =
        ElasticRepository.getTransactions(request)
}