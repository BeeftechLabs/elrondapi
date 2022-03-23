package com.beeftechlabs.processing

import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.ScResult
import com.beeftechlabs.model.transaction.Transaction
import com.beeftechlabs.model.transaction.TransactionType
import com.beeftechlabs.plugins.endCustomTrace
import com.beeftechlabs.plugins.startCustomTrace
import com.beeftechlabs.processing.TransactionProcessor.groupedByToken
import com.beeftechlabs.util.fromBase64String
import com.beeftechlabs.util.fromHexString
import com.beeftechlabs.util.tokenFromIdentifier
import mu.KotlinLogging

object TransactionProcessor {

    suspend fun process(transaction: Transaction): Transaction {
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

    private suspend fun parseESDTTransfer(transaction: Transaction, args: List<String>): Transaction {
        startCustomTrace("parseESDTTransfer:${transaction.hash}")
        val outValuesRaw = extractTransactionValues(transaction)
        val outValues = outValuesRaw.map { it.copy(token = it.token.tokenFromIdentifier()) }.groupedByToken()

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.copy(data = it.data.fromBase64String()) }
        val inValuesRaw = extractAllESDTTransferTypeValues(relevantScResults)
        val inValues = inValuesRaw.map { it.copy(token = it.token.tokenFromIdentifier()) }.groupedByToken()

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
            outValuesRaw = outValuesRaw,
            inValues = inValues,
            inValuesRaw = inValuesRaw,
            type = transactionType,
            function = function
        ).also {
            endCustomTrace("parseESDTTransfer:${transaction.hash}")
        }
    }

    private suspend fun parseESDTNFTTransfer(transaction: Transaction, args: List<String>): Transaction {
        startCustomTrace("parseESDTNFTTransfer:${transaction.hash}")
        val outValuesRaw = extractTransactionValues(transaction)
        val outValues = outValuesRaw.map { it.copy(token = it.token.tokenFromIdentifier()) }.groupedByToken()

        val otherAddress =
            if (transaction.receiver == transaction.sender) Address(args[4]).erd else transaction.receiver

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.copy(data = it.data.fromBase64String()) }
        val inValuesRaw = extractAllESDTTransferTypeValues(relevantScResults)
        val inValues = inValuesRaw.map { it.copy(token = it.token.tokenFromIdentifier()) }.groupedByToken()

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
            outValuesRaw = outValuesRaw,
            outValues = outValues,
            inValues = inValues,
            inValuesRaw = inValuesRaw,
            type = transactionType,
            receiver = otherAddress,
            function = function
        ).also {
            endCustomTrace("parseESDTNFTTransfer:${transaction.hash}")
        }
    }

    private suspend fun parseMultiESDTNFTTransfer(transaction: Transaction, args: List<String>): Transaction {
        startCustomTrace("parseMultiESDTNFTTransfer:${transaction.hash}")
        val outValuesRaw = extractTransactionValues(transaction)
        val outValues = outValuesRaw.map { it.copy(token = it.token.tokenFromIdentifier()) }.groupedByToken()

        val otherAddress =
            if (transaction.receiver == transaction.sender) Address(args[1]).erd else transaction.receiver

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.copy(data = it.data.fromBase64String()) }
        var inValuesRaw = extractAllESDTTransferTypeValues(relevantScResults)
        var inValues = inValuesRaw.map { it.copy(token = it.token.tokenFromIdentifier()) }.groupedByToken()

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

                // I think there's a bug somewhere in an SC for mergeLockedAssetTokens
                if (relevantScResults.all { it.data.endsWith("@657865637574696f6e206661696c6564") }) { // execution failed
                    inValues = outValues
                    inValuesRaw = outValuesRaw
                }
            }
        }

        return transaction.copy(
            outValues = outValues,
            outValuesRaw = outValuesRaw,
            inValues = inValues,
            inValuesRaw = inValuesRaw,
            type = transactionType,
            sender = transaction.sender,
            receiver = otherAddress,
            function = function
        ).also {
            endCustomTrace("parseMultiESDTNFTTransfer:${transaction.hash}")
        }
    }

    private suspend fun parseOtherTransactions(transaction: Transaction, args: List<String>): Transaction {
        startCustomTrace("parseOtherTransactions:${transaction.hash}")
        var outValue: Value? = null
        val transactionType: TransactionType
        val function = args.firstOrNull()

        val relevantScResults = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.copy(data = it.data.fromBase64String()) }
        var inValuesRaw = extractAllESDTTransferTypeValues(relevantScResults)
        var inValues = inValuesRaw.map { it.copy(token = it.token.tokenFromIdentifier()) }.groupedByToken()

        when (function) {
            "delegate", "stake" -> {
                outValue = transaction.transactionValue
                transactionType = TransactionType.Delegate
            }
            "modifyTotalDelegationCap" -> {
                outValue = args.getOrNull(1)?.let { Value.extractHex(it, "EGLD") } ?: Value.None
                transactionType = TransactionType.ModifyDelegationCap
            }
            "claimRewards" -> {
                transaction.scResults.firstOrNull { it.data.isEmpty() }?.let {
                    inValues = listOfNotNull(Value.extract(it.value, "EGLD"))
                    inValuesRaw = inValues
                }
                transactionType = TransactionType.Claim
            }
            "unDelegate", "unStake" -> {
                outValue = args.getOrNull(1)?.let { Value.extractHex(it, "EGLD") } ?: Value.None
                transactionType = TransactionType.Undelegate
            }
            "reDelegateRewards" -> {
                transaction.scResults.firstOrNull { it.data.isEmpty() }?.let {
                    outValue = Value.extract(it.value, "EGLD")
                }
                transactionType = TransactionType.Compound
            }
            "withdraw", "unBond" -> {
                transaction.scResults
                    .filter { it.receiver == transaction.sender }
                    .firstOrNull { it.data.isEmpty() }?.let {
                        inValues = listOfNotNull(Value.extract(it.value, "EGLD"))
                        inValuesRaw = inValues
                    }
                transactionType = TransactionType.Withdraw
            }
            "changeServiceFee" -> {
                outValue = args.getOrNull(1)
                    ?.let { Value("", 0, it.toIntOrNull(16)?.toDouble(), "") } ?: Value.None
                transactionType = TransactionType.ChangeServiceFee
            }
            "wrapEgld" -> {
                outValue = transaction.transactionValue
                transactionType = TransactionType.Wrap
            }
            else -> {
                if (transaction.sender == METACHAIN) {
                    outValue = transaction.transactionValue
                    transactionType = TransactionType.ReceiveValidationReward
                } else {
                    // SC Call
                    transactionType = TransactionType.SmartContract
                }
            }
        }

        return transaction.copy(
            outValues = listOfNotNull(outValue),
            outValuesRaw = listOfNotNull(outValue),
            inValues = inValues,
            inValuesRaw = inValuesRaw,
            type = transactionType,
            function = function
        ).also {
            endCustomTrace("parseOtherTransactions:${transaction.hash}")
        }
    }

    private suspend fun extractAllESDTTransferTypeValues(scResults: List<ScResult>): List<Value> {
        return scResults.filter { it.data.isRelevantTransfer() }.map { scResult ->
            if (scResult.tokens.isNotEmpty()) {
                scResult.tokens.mapIndexedNotNull { index, token -> Value.extract(scResult.esdtValues[index], token) }
            } else {
                extractValuesFromData(scResult)
            }
        }.flatten()
    }

    private suspend fun extractTransactionValues(transaction: Transaction): List<Value> {
        // TODO not working
//        if (transaction.tokens.isNotEmpty()) {
//            return transaction.tokens.mapIndexedNotNull { index, token ->
//                Value.extract(
//                    transaction.esdtValues[index],
//                    token
//                )
//            }
//        } else {
            val scArgs = transaction.data.fromBase64String().split("@")
            return when (scArgs.firstOrNull()) {
                "ESDTTransfer" -> listOfNotNull(extractESDTTransferValue(scArgs))
                "ESDTNFTTransfer" -> listOfNotNull(extractESDTNFTTransferValue(scArgs))
                "MultiESDTNFTTransfer" -> extractMultiESDTNFTTransferValue(scArgs)
                "" -> listOfNotNull(transaction.transactionValue.takeIf { (it.denominated ?: 0.0) > 0 })
                else -> emptyList()
            }
//        }
    }

    private suspend fun extractValuesFromData(scResult: ScResult): List<Value> {
        val scArgs = scResult.data.split("@")
        return when (scArgs.firstOrNull()) {
            "ESDTTransfer" -> listOfNotNull(extractESDTTransferValue(scArgs))
            "ESDTNFTTransfer" -> listOfNotNull(extractESDTNFTTransferValue(scArgs))
            "MultiESDTNFTTransfer" -> extractMultiESDTNFTTransferValue(scArgs)
            "" -> listOfNotNull(Value.extract(scResult.value, "EGLD").takeIf { (it?.denominated ?: 0.0) > 0 })
            else -> emptyList()
        }
    }

    private suspend fun extractESDTTransferValue(args: List<String>): Value? {
        return Value.extractHex(args[2], args[1].fromHexString())
    }

    private suspend fun extractESDTNFTTransferValue(args: List<String>): Value? {
        return Value.extractHex(args[3], "${args[1].fromHexString()}-${args[2]}")
    }

    private suspend fun extractMultiESDTNFTTransferValue(args: List<String>): List<Value> {
        try {
            val numTokens = args[2].toInt(16)

            return (1..numTokens).mapNotNull { Value.extractHex(args[3 * it + 2], "${args[3 * it].fromHexString()}-${args[3 * it + 1]}") }
                .also { values ->
                    if (values.contains(Value.None)) {
                        logger.error { "Error while parsing MultiESDTNFTTransfer values from $args" }
                    }
                }
        } catch (ignored: Exception) {
            val numTokens = args[1].toInt(16)

            return (1..numTokens).mapNotNull { Value.extractHex(args[3 * it + 1], "${args[3 * it - 1].fromHexString()}-${args[3 * it]}") }
                .also { values ->
                    if (values.contains(Value.None)) {
                        logger.error { "Error while parsing MultiESDTNFTTransfer values from $args" }
                    }
                }
        }
    }

    private fun List<Value>.groupedByToken(): List<Value> =
        groupBy { it.token }.map { (_, values) ->
            values.reduce { acc, value ->
                acc + value
            }
        }

    private fun String.isRelevantTransfer() =
        startsWith("ESDTTransfer") || startsWith("ESDTNFTTransfer") || startsWith("MultiESDTNFTTransfer") || isEmpty()

    private const val METACHAIN = "4294967295"

    private val logger = KotlinLogging.logger {}
}