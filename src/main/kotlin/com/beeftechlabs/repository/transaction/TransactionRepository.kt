package com.beeftechlabs.repository.transaction

import com.beeftechlabs.model.address.toAddress
import com.beeftechlabs.model.transaction.*
import com.beeftechlabs.repository.elastic.ElasticRepository
import com.beeftechlabs.service.GatewayService

object TransactionRepository {

    suspend fun getTransaction(hash: String, process: Boolean): Transaction? =
        ElasticRepository.getTransaction(hash, process)

    suspend fun getTransactionState(hash: String, checkCompletedTXEvent: Boolean): NewTransactionState {
        val response = GatewayService.get<GetTransactionResponse>("transaction/$hash?withResults=true")
        val transaction = response.data.transaction

        var txStatus = when (transaction.status) {
            "success" -> NewTransactionStatus.Success
            "invalid" -> NewTransactionStatus.Failed
            else -> NewTransactionStatus.Pending
        }

        if (txStatus == NewTransactionStatus.Success && checkCompletedTXEvent) {
            val allEvents = transaction.allEvents()
            if (allEvents.any { it.identifier == "signalError" }) {
                txStatus = NewTransactionStatus.Failed
            } else if (allEvents.none { it.identifier == "completedTxEvent" }) {
                txStatus = NewTransactionStatus.Pending
            }
        }

        return NewTransactionState(
            hash = hash,
            receiverShard = transaction.destinationShard,
            senderShard = transaction.sourceShard,
            status = txStatus,
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