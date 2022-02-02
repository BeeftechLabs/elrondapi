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

    fun process(transaction: Transaction): Transaction {
        return if (!transaction.isScCall && !transaction.hasScResults) {
            transaction.copy(
                type = TransactionType.Transfer,
                outValues = listOf(transaction.transactionValue),
                inValues = emptyList()
            )
        } else {
            val data = transaction.data.fromBase64String()
            val args = data.split("@")

            args.firstOrNull()?.let { function ->
                when (function) {
                    "ESDTTransfer" -> parseESDTTransfer(transaction, args)
                    "ESDTNFTTransfer" -> parseESDTNFTTransfer(transaction, args)
                    "MultiESDTNFTTransfer" -> parseMultiESDTNFTTransfer(transaction, args)
                    else -> parseOtherTransactions(transaction, args)
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

    private fun parseESDTTransfer(transaction: Transaction, args: List<String>): Transaction {
        val outValues = listOf(extractESDTTransferValue(args))

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.copy(data = it.data.fromBase64String()) }
        val inValues = extractAllESDTTransferTypeValues(relevantScResults).groupedByToken()

        val transactionType: TransactionType

        val function = args.getOrNull(3)?.fromHexString()

        when (function) {
            "swapTokensFixedInput", "swapTokensFixedOutput" -> {
                transactionType = TransactionType.Swap
            }
            "unwrapEgld" -> {
                transactionType = TransactionType.Unwrap
            }
            "removeLiquidity" -> {
                transactionType = TransactionType.ExitLP
            }
            null -> {
                transactionType = TransactionType.Transfer
            }
            else -> {
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

    private fun parseESDTNFTTransfer(transaction: Transaction, args: List<String>): Transaction {
        val outValues = listOf(extractESDTNFTTransferValue(args))

        val otherAddress = if (transaction.receiver == transaction.sender) Address(args[4]).erd else transaction.receiver

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.copy(data = it.data.fromBase64String()) }
        val inValues = extractAllESDTTransferTypeValues(relevantScResults).groupedByToken()

        val transactionType: TransactionType

        val function = args.getOrNull(5)?.fromHexString()

        when (function) {
            "claimRewardsProxy", "claimRewards", "unlockAssets" -> {
                transactionType = TransactionType.Claim
            }
            "compoundRewards", "compoundRewardsProxy" -> {
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
                transactionType = TransactionType.Transfer
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
            receiver = otherAddress,
            function = function
        )
    }

    private fun parseMultiESDTNFTTransfer(transaction: Transaction, args: List<String>): Transaction {
        val outValues = extractMultiESDTNFTTransferValue(args)

        val otherAddress = if (transaction.receiver == transaction.sender) Address(args[1]).erd else transaction.receiver

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.copy(data = it.data.fromBase64String()) }
        val inValues = extractAllESDTTransferTypeValues(relevantScResults).groupedByToken()

        val transactionType: TransactionType

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
                transactionType = TransactionType.Transfer
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
            sender = transaction.sender,
            receiver = otherAddress,
            function = function
        )
    }

    private fun parseOtherTransactions(transaction: Transaction, args: List<String>): Transaction {
        var value: Value? = null
        val transactionType: TransactionType
        val function = args.firstOrNull()

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
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
                    .filter { it.receiver == transaction.sender }
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
        try {
            val numTokens = args[2].toInt(16)

            return (1..numTokens).map { Value.extractHex(args[3 * it + 2], args[3 * it].tokenFromArg()) }.also { values ->
                if (values.contains(Value.None)) {
                    logger.error { "Error while parsing MultiESDTNFTTransfer values from $args" }
                }
            }
        } catch (ignored: Exception) {
            val numTokens = args[1].toInt(16)

            return (1..numTokens).map { Value.extractHex(args[3 * it + 1], args[3 * it - 1].tokenFromArg()) }.also { values ->
                if (values.contains(Value.None)) {
                    logger.error { "Error while parsing MultiESDTNFTTransfer values from $args" }
                }
            }
        }
    }

    private fun String.isRelevantTransfer() =
        startsWith("ESDTTransfer") || startsWith("ESDTNFTTransfer") || startsWith("MultiESDTNFTTransfer") || isEmpty()

    private const val METACHAIN = "4294967295"

    private val logger = KotlinLogging.logger {}
}