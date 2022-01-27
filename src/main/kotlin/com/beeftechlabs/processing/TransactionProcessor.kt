package com.beeftechlabs.processing

import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.Transaction
import com.beeftechlabs.model.transaction.TransactionType
import com.beeftechlabs.util.fromBase64String
import com.beeftechlabs.util.fromHexString
import com.beeftechlabs.util.tokenFromArg

object TransactionProcessor {

    fun process(filterAddress: String, transaction: Transaction): Transaction {
        val referenceAddress = filterAddress.ifEmpty { transaction.sender }

        return if (!transaction.isScCall && !transaction.hasScResults) {
            if (transaction.sender == referenceAddress) {
                transaction.copy(type = TransactionType.Send)
            } else {
                transaction.copy(type = TransactionType.Receive)
            }.copy(
                value = transaction.transactionValue
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
        val token = args[1].fromHexString().split("-").first()
        var value = Value.extractHex(args[2], token)
        val scResultsRelevantData = transaction.scResults
            .filter { it.receiver == referenceAddress }
            .map { it.data.fromBase64String() }
        var otherValue = Value.None
        var transactionType = TransactionType.Unknown

        val isSender = referenceAddress == transaction.sender

        val function = args.getOrNull(3)?.fromHexString()

        when (function) {
            "swapTokensFixedInput", "swapTokensFixedOutput" -> {
                val values = extractAllESDTTransferTypeValues(scResultsRelevantData)
                values.filter { it.token == value.token }.forEach { value -= it }
                val otherValues = values.filterNot { it.token == value.token }
                if (otherValues.isNotEmpty()) {
                    otherValue = Value.zero(otherValues.first().token)
                    otherValues.forEach { otherValue += it }
                }
                transactionType = TransactionType.Swap
            }
            "unwrapEgld" -> {
                transactionType = TransactionType.Unwrap
            }
            "removeLiquidity" -> {
                val values = extractAllESDTTransferTypeValues(scResultsRelevantData)
                if (values.isNotEmpty()) {
                    value = values.first()
                }
                if (values.size > 1) {
                    otherValue = values[1]
                }
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
            value = value,
            otherValue = otherValue,
            type = transactionType,
            function = function
        )
    }

    private fun parseESDTNFTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        val token = args[1].fromHexString().split("-").first()
        var value = Value.extractHex(args[3], token)
        val scResultsRelevantData = transaction.scResults
            .filter { it.receiver == referenceAddress }
            .map { it.data.fromBase64String() }
        var otherValue = Value.None
        var transactionType = TransactionType.Unknown

        val otherAddress = Address(args[4]).erd
        val isSender = transaction.sender == referenceAddress

        val function = args.getOrNull(5)?.fromHexString()

        when (function) {
            "claimRewardsProxy", "claimRewards", "unlockAssets" -> {
                val values = extractAllESDTTransferTypeValues(scResultsRelevantData)
                values.firstOrNull()?.let { value = it }
                if (values.size > 1) {
                    otherValue = values[1]
                }
                transactionType = TransactionType.Claim
            }
            "compoundRewards", "compoundRewardsProxy" -> {
                value = extractESDTNFTTransferValue(transaction.data.fromBase64String())
                transactionType = TransactionType.Compound
            }
            "exitFarmProxy", "exitFarm" -> {
                val values = extractAllESDTTransferTypeValues(scResultsRelevantData)
                values.firstOrNull()?.let { value = it }
                if (values.size > 1) {
                    otherValue = values[1]
                }
                transactionType = TransactionType.ExitFarm
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
            value = value,
            otherValue = otherValue,
            type = transactionType,
            sender = if (isSender) transaction.sender else otherAddress,
            receiver = if (!isSender) transaction.receiver else otherAddress,
            function = function
        )
    }

    private fun parseMultiESDTNFTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        val token = args[3].fromHexString().split("-").first()
        var value = Value.extractHex(args[5], token)
        var otherValue = Value.None
        var transactionType = TransactionType.Unknown

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
                val values = extractMultiESDTNFTTransferValue(transaction.data.fromBase64String())
                values.firstOrNull()?.let { value = it }
                if (values.size > 1) {
                    otherValue = values[1]
                }
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
            value = value,
            otherValue = otherValue,
            type = transactionType,
            sender = if (isSender) transaction.sender else otherAddress,
            receiver = if (!isSender) transaction.receiver else otherAddress,
            function = function
        )
    }

    private fun parseOtherTransactions(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        var value = Value.None
        val transactionType: TransactionType
        val function = args.firstOrNull()

        when (function) {
            "delegate", "stake" -> {
                value = transaction.transactionValue
                transactionType = TransactionType.Delegate
            }
            "modifyTotalDelegationCap" -> {
                value = Value.extractHex(args[1], "EGLD")
                transactionType = TransactionType.ModifyDelegationCap
            }
            "claimRewards" -> {
                transaction.scResults.firstOrNull { it.data.isEmpty() }?.let {
                    value = Value.extract(it.value, "EGLD")
                }
                transactionType = TransactionType.Claim
            }
            "unDelegate", "unStake" -> {
                value = Value.extractHex(args[1], "EGLD")
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
                value = Value.extractHex(args[1], "EGLD")
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
            value = value,
            type = transactionType,
            function = function
        )
    }

    private fun extractAllESDTTransferTypeValues(datas: List<String>): List<Value> {
        return datas.filter { it.isESDTTypeTransfer() }.mapNotNull { data ->
            val scArgs = data.split("@")
            when (scArgs[0]) {
                "ESDTTransfer" -> listOf(extractESDTTransferValue(data))
                "ESDTNFTTransfer" -> listOf(extractESDTNFTTransferValue(data))
                "MultiESDTNFTTransfer" -> extractMultiESDTNFTTransferValue(data)
                else -> null
            }
        }.flatten()
    }

    private fun extractESDTTransferValue(data: String): Value {
        val args = data.split("@")
        return Value.extractHex(args[2], args[1].tokenFromArg())
    }

    private fun extractESDTNFTTransferValue(data: String): Value {
        val args = data.split("@")
        return Value.extractHex(args[3], args[1].tokenFromArg())
    }

    private fun extractMultiESDTNFTTransferValue(data: String): List<Value> {
        val args = data.split("@")
        val numTokens = args[2].toInt(16)

        return (1..numTokens).map { Value.extractHex(args[3 * it + 2], args[3 * it].tokenFromArg()) }
    }

    private fun String.isESDTTypeTransfer() =
        startsWith("ESDTTransfer") || startsWith("ESDTNFTTransfer") || startsWith("MultiESDTNFTTransfer")

    private const val METACHAIN = "4294967295"
}