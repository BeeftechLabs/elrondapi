package com.beeftechlabs.repository.transaction

import com.beeftechlabs.model.address.toAddress
import com.beeftechlabs.model.transaction.*
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.service.GatewayService

object TransactionRepository {

    suspend fun getTransaction(hash: String, process: Boolean): Transaction? =
        ElasticRepository.getTransaction(hash, process)

    suspend fun getTransactions(request: TransactionsRequest): TransactionsResponse =
        ElasticRepository.getTransactions(request)

    suspend fun sendTransaction(transaction: NewTransaction): NewTransactionState {
        val senderShard = transaction.sender.toAddress().shard
        val receiverShard = transaction.receiver.toAddress().shard

        val response: PostTransactionResponse = GatewayService.post("transaction/send", transaction)

        return NewTransactionState(
            hash = response.data.txHash,
            receiverShard = receiverShard,
            senderShard = senderShard,
            status = if (response.data.txHash != null) NewTransactionStatus.Pending else NewTransactionStatus.Failed,
            error = response.data.error
        )
    }
}