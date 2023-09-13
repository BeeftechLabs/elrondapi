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
    val logs: TXSCResultLogs? = null,
    val smartContractResults: List<TxSmartContractResult> = emptyList()
) {
    fun allEvents(): List<TXSCRLEvent> = smartContractResults.flatMap { it.logs?.events ?: emptyList() } +
            (logs?.events ?: emptyList())
}

@Serializable
data class TxSmartContractResult(
    val logs: TXSCResultLogs? = null
)

@Serializable
data class TXSCResultLogs(
    val events: List<TXSCRLEvent> = emptyList()
)

@Serializable
data class TXSCRLEvent(
    val address: String,
    val identifier: String
)