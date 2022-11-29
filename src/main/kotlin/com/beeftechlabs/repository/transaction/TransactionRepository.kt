package com.beeftechlabs.repository.transaction

import com.beeftechlabs.model.address.toAddress
import com.beeftechlabs.model.transaction.*
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.service.GatewayService

object TransactionRepository {

    suspend fun getTransaction(hash: String, process: Boolean): Transaction? =
        ElasticRepository.getTransaction(hash, process)

    suspend fun getTransactionState(hash: String): NewTransactionState {
        val response = GatewayService.get<GetTransactionResponse>("transaction/$hash")
        val transaction = response.data.transaction
        return NewTransactionState(
            hash = hash,
            receiverShard = transaction.destinationShard,
            senderShard = transaction.sourceShard,
            status = when (transaction.status) {
                "success" -> NewTransactionStatus.Success
                "invalid" -> NewTransactionStatus.Failed
                else -> NewTransactionStatus.Pending
            },
            error = response.error
        )
    }

    suspend fun getTransactions(request: TransactionsRequest): TransactionsResponse =
        ElasticRepository.getTransactions(request)

    suspend fun sendTransaction(transaction: NewTransaction): NewTransactionState {
        val tx = if (transaction.chainID.isNullOrEmpty()) {
            transaction.copy(chainID = transaction.chainId)
        } else {
            transaction
        }

        val senderShard = tx.sender.toAddress().shard
        val receiverShard = tx.receiver.toAddress().shard

        val response: PostTransactionResponse = GatewayService.post("transaction/send", tx)

        return NewTransactionState(
            hash = response.data?.txHash,
            receiverShard = receiverShard,
            senderShard = senderShard,
            status = if (response.data?.txHash != null) NewTransactionStatus.Pending else NewTransactionStatus.Failed,
            error = response.data?.error ?: response.error
        )
    }
}