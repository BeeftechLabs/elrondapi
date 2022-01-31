package com.beeftechlabs.processing

import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.ScResult
import com.beeftechlabs.model.transaction.Transaction
import com.beeftechlabs.model.transaction.TransactionType
import com.beeftechlabs.util.fromBase64String
import com.beeftechlabs.util.fromHexString
import com.beeftechlabs.util.tokenFromArg
import mu.KotlinLogging

object TransactionProcessor {

    fun process(filterAddress: String, transaction: Transaction): Transaction {
        val referenceAddress = filterAddress.ifEmpty { transaction.sender }

        return if (!transaction.isScCall && !transaction.hasScResults) {
            if (transaction.sender == referenceAddress) {
                transaction.copy(type = TransactionType.Send)
            } else {
                transaction.copy(type = TransactionType.Receive)
            }.copy(
                outValues = if (transaction.sender == referenceAddress) {
                    listOf(transaction.transactionValue)
                } else {
                    emptyList()
                },
                inValues = if (transaction.sender == referenceAddress) {
                    emptyList()
                } else {
                    listOf(transaction.transactionValue)
                }
            )
        } else {
            val data = transaction.data.fromBase64String()
            val args = data.split("@")

            args.firstOrNull()?.let { function ->
                when (function) {
                    "ESDTTransfer" -> parseESDTTransfer(referenceAddress, transaction, args)
                    "ESDTNFTTransfer" -> parseESDTNFTTransfer(referenceAddress, transaction, args)
                    "MultiESDTNFTTransfer" -> parseMultiESDTNFTTransfer(referenceAddress, transaction, args)
                    else -> parseOtherTransactions(referenceAddress, transaction, args)
                }.let { processed ->
                    if (processed.function.isNullOrEmpty()) {
                        processed.copy(function = function)
                    } else {
                        processed
                    }
                }
            } ?: transaction
        }
    }

    private fun parseESDTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        var outValues = listOf(extractESDTTransferValue(args))

        val relevantScResults = transaction.scResults
            .filter { it.receiver == referenceAddress }
            .map { it.copy(data = it.data.fromBase64String()) }
        var inValues = extractAllESDTTransferTypeValues(relevantScResults).groupedByToken()

        val transactionType: TransactionType

        val isSender = referenceAddress == transaction.sender

        val function = args.getOrNull(3)?.fromHexString()

        when (function) {
            "swapTokensFixedInput", "swapTokensFixedOutput" -> {
                val (processedOut, processedIn) = outValues - inValues
                outValues = processedOut
                inValues = processedIn
                transactionType = TransactionType.Swap
            }
            "unwrapEgld" -> {
                transactionType = TransactionType.Unwrap
            }
            "removeLiquidity" -> {
                transactionType = TransactionType.ExitLP
            }
            null -> {
                // normal transfer
                transactionType = if (isSender) TransactionType.Send  else TransactionType.Receive
            }
            else -> {
                // Some SC call with ESDT
                transactionType = TransactionType.SmartContract
            }
        }

        return transaction.copy(
            outValues = outValues,
            inValues = inValues,
            type = transactionType,
            function = function
        )
    }

    private fun parseESDTNFTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        var outValues = listOf(extractESDTNFTTransferValue(args))

        val relevantScResults = transaction.scResults
            .filter { it.receiver == referenceAddress }
            .map { it.copy(data = it.data.fromBase64String()) }
        var inValues = extractAllESDTTransferTypeValues(relevantScResults).groupedByToken()

        val transactionType: TransactionType

        val otherAddress = Address(args[4]).erd
        val isSender = transaction.sender == referenceAddress

        val function = args.getOrNull(5)?.fromHexString()

        when (function) {
            "claimRewardsProxy", "claimRewards", "unlockAssets" -> {
                val (processedIn, processedOut) = inValues - outValues
                inValues = processedIn
                outValues = processedOut

                transactionType = TransactionType.Claim
            }
            "compoundRewards", "compoundRewardsProxy" -> {
                val (processedIn, processedOut) = inValues - outValues
                inValues = processedIn
                outValues = processedOut

                transactionType = TransactionType.Compound
            }
            "exitFarmProxy", "exitFarm" -> {
                transactionType = TransactionType.ExitFarm
            }
            "removeLiquidityProxy" -> {
                transactionType = TransactionType.ExitLP
            }
            null -> {
                // normal transfer
                transactionType = if (isSender) TransactionType.Send else TransactionType.Receive
            }
            else -> {
                // Some SC NFT call
                transactionType = TransactionType.SmartContract
            }
        }

        return transaction.copy(
            outValues = outValues,
            inValues = inValues,
            type = transactionType,
            sender = if (isSender) transaction.sender else otherAddress,
            receiver = if (!isSender) transaction.receiver else otherAddress,
            function = function
        )
    }

    private fun parseMultiESDTNFTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        val outValues = extractMultiESDTNFTTransferValue(args)

        val relevantScResults = transaction.scResults
            .filter { it.receiver == referenceAddress }
            .map { it.copy(data = it.data.fromBase64String()) }
        val inValues = extractAllESDTTransferTypeValues(relevantScResults).groupedByToken()

        val transactionType: TransactionType

        val otherAddress = Address(args[1]).erd
        val isSender = transaction.sender == referenceAddress

        val numTokens = args[2].toInt(16)
        val functionIdx = 3 + numTokens * 3
        val function = args.getOrNull(functionIdx)?.fromHexString()

        when (function) {
            "enterFarmProxy", "enterFarm", "enterFarmAndLockRewards", "enterFarmAndLockRewardsProxy" -> {
                transactionType = TransactionType.EnterFarm
            }
            "addLiquidity", "addLiquidityProxy" -> {
                transactionType = TransactionType.EnterLP
            }
            null -> {
                // normal transfer
                transactionType = if (isSender) TransactionType.Send else TransactionType.Receive
            }
            else -> {
                // some MultiESDT SC call
                transactionType = TransactionType.SmartContract
            }
        }

        return transaction.copy(
            outValues = outValues,
            inValues = inValues,
            type = transactionType,
            sender = if (isSender) transaction.sender else otherAddress,
            receiver = if (!isSender) transaction.receiver else otherAddress,
            function = function
        )
    }

    private fun parseOtherTransactions(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        var value: Value? = null
        val transactionType: TransactionType
        val function = args.firstOrNull()

        val relevantScResults = transaction.scResults
            .filter { it.receiver == referenceAddress }
            .map { it.copy(data = it.data.fromBase64String()) }
        val inValues = extractAllESDTTransferTypeValues(relevantScResults).groupedByToken()

        when (function) {
            "delegate", "stake" -> {
                value = transaction.transactionValue
                transactionType = TransactionType.Delegate
            }
            "modifyTotalDelegationCap" -> {
                value = args.getOrNull(1)?.let { Value.extractHex(it, "EGLD") } ?: Value.None
                transactionType = TransactionType.ModifyDelegationCap
            }
            "claimRewards" -> {
                transaction.scResults.firstOrNull { it.data.isEmpty() }?.let {
                    value = Value.extract(it.value, "EGLD")
                }
                transactionType = TransactionType.Claim
            }
            "unDelegate", "unStake" -> {
                value = args.getOrNull(1)?.let { Value.extractHex(it, "EGLD") } ?: Value.None
                transactionType = TransactionType.Undelegate
            }
            "reDelegateRewards" -> {
                transaction.scResults.firstOrNull { it.data.isEmpty() }?.let {
                    value = Value.extract(it.value, "EGLD")
                }
                transactionType = TransactionType.Compound
            }
            "withdraw", "unBond" -> {
                transaction.scResults
                    .filter { it.receiver == referenceAddress }
                    .firstOrNull { it.data.isEmpty() }?.let {
                        value = Value.extract(it.value, "EGLD")
                    }
                transactionType = TransactionType.Withdraw
            }
            "changeServiceFee" -> {
                value = args.getOrNull(1)?.let { Value.extractHex(it, "EGLD") } ?: Value.None
                transactionType = TransactionType.ChangeServiceFee
            }
            "wrapEgld" -> {
                value = transaction.transactionValue
                transactionType = TransactionType.Wrap
            }
            else -> {
                if (transaction.sender == METACHAIN) {
                    value = transaction.transactionValue
                    transactionType = TransactionType.ReceiveValidationReward
                } else {
                    // SC Call
                    transactionType = TransactionType.SmartContract
                }
            }
        }

        return transaction.copy(
            outValues = listOfNotNull(value),
            inValues = inValues,
            type = transactionType,
            function = function
        )
    }

    private fun extractAllESDTTransferTypeValues(scResults: List<ScResult>): List<Value> {
        return scResults.filter { it.data.isRelevantTransfer() }.mapNotNull { scResult ->
            val data = scResult.data
            val scArgs = data.split("@")
            when (scArgs.firstOrNull()) {
                "ESDTTransfer" -> listOf(extractESDTTransferValue(data))
                "ESDTNFTTransfer" -> listOf(extractESDTNFTTransferValue(data))
                "MultiESDTNFTTransfer" -> extractMultiESDTNFTTransferValue(data)
                "" -> listOf(Value.extract(scResult.value, "EGLD"))
                else -> null
            }
        }.flatten()
    }

    private fun List<Value>.groupedByToken(): List<Value> =
        groupBy { it.token }.map { (_, values) ->
            values.reduce { acc, value ->
                acc + value
            }
        }

    private operator fun List<Value>.minus(other: List<Value>): Pair<List<Value>, List<Value>> {
        val all = mutableListOf<Value>()
        val remaining = other.toMutableList()

        forEach { value ->
            val rhs = remaining.find { it.token == value.token } ?: Value.zero(value.token)
            val rem = value - rhs
            if ((rem.denominated ?: 0.0) > 0) {
                all.add(rem)
            }
            remaining.remove(rhs)
        }

        return all.filter { (it.denominated ?: 0.0) > 0 } to (remaining + all.filter { (it.denominated ?: 0.0) <= 0 }.map { it.abs() })
    }

    private fun extractESDTTransferValue(data: String): Value =
        extractESDTTransferValue(data.split("@"))

    private fun extractESDTTransferValue(args: List<String>): Value {
        return Value.extractHex(args[2], args[1].tokenFromArg())
    }

    private fun extractESDTNFTTransferValue(data: String): Value =
        extractESDTNFTTransferValue(data.split("@"))

    private fun extractESDTNFTTransferValue(args: List<String>): Value {
        return Value.extractHex(args[3], args[1].tokenFromArg())
    }

    private fun extractMultiESDTNFTTransferValue(data: String): List<Value> =
        extractMultiESDTNFTTransferValue(data.split("@"))

    private fun extractMultiESDTNFTTransferValue(args: List<String>): List<Value> {
        val numTokens = args[2].toInt(16)

        return (1..numTokens).map { Value.extractHex(args[3 * it + 2], args[3 * it].tokenFromArg()) }.also { values ->
            if (values.contains(Value.None)) {
                logger.error { "Error while parsing MultiESDTNFTTransfer values from $args" }
            }
        }
    }

    private fun String.isRelevantTransfer() =
        startsWith("ESDTTransfer") || startsWith("ESDTNFTTransfer") || startsWith("MultiESDTNFTTransfer") || isEmpty()

    private const val METACHAIN = "4294967295"

    private val logger = KotlinLogging.logger {}
}