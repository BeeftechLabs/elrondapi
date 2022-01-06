package com.beeftechlabs.processing

import com.beeftechlabs.model.Transaction
import com.beeftechlabs.model.TransactionType
import com.beeftechlabs.model.Value
import com.beeftechlabs.util.Address
import com.beeftechlabs.util.fromBase64String
import com.beeftechlabs.util.fromHexString
import com.beeftechlabs.util.tokenFromArg

object TransactionProcessor {

    fun process(referenceAddress: String, transaction: Transaction): Transaction {
        return if (!transaction.hasScResults) {
            if (transaction.sender == referenceAddress) {
                transaction.copy(type = TransactionType.Send)
            } else {
                transaction.copy(type = TransactionType.Receive)
            }
        } else {
            val data = transaction.data.fromBase64String()
            val args = data.split("@")
            args.firstOrNull()?.let { function ->
                when (function) {
                    "ESDTTransfer" -> parseESDTTransfer(referenceAddress, transaction, args)
                    "ESDTNFTTransfer" -> parseESDTNFTTransfer(referenceAddress, transaction, args)
                    "MultiESDTNFTTransfer" -> parseMultiESDTNFTTransfer(referenceAddress, transaction, args)
                    else -> parseOtherTransactions(referenceAddress, transaction, args)
                }
            } ?: transaction
        }
    }

    private fun parseESDTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        val token = args[1].fromHexString().split("-").first()
        var value = Value.extractHex(args[2], token)
        val scResultsRelevantData = transaction.scResults
            .filter { it.receiver == transaction.sender }
            .map { it.data.fromBase64String() }
        var otherValue = Value.None
        var transactionType = TransactionType.Unknown
        when (args.getOrNull(3)?.fromHexString()) {
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
                transactionType = if (referenceAddress == transaction.sender) TransactionType.Send else TransactionType.Receive
            }
        }

        return transaction.copy(
            value = value,
            otherValue = otherValue,
            type = transactionType
        )
    }

    private fun parseESDTNFTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        val token = args[1].fromHexString().split("-").first()
        var value = Value.extractHex(args[2], token)
        val scResultsRelevantData = transaction.scResults
            .filter { it.receiver == referenceAddress }
            .map { it.data.fromBase64String() }
        var otherValue = Value.None
        var transactionType = TransactionType.Unknown

        val otherAddress = Address(args[4]).erd
        val isSender = transaction.sender == referenceAddress

        when (args.getOrNull(5)?.fromHexString()) {
            "claimRewardsProxy", "claimRewards", "unlockAssets" -> {
                val values = extractAllESDTTransferTypeValues(scResultsRelevantData)
                value = values.first()
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
                value = values.first()
                if (values.size > 1) {
                    otherValue = values[1]
                }
                transactionType = TransactionType.ExitFarm
            }
            null -> {
                // normal transfer
                transactionType = if (referenceAddress == transaction.sender) TransactionType.Send else TransactionType.Receive
            }
        }

        return transaction.copy(
            value = value,
            otherValue = otherValue,
            type = transactionType,
            sender = if (isSender) transaction.sender else otherAddress,
            receiver = if (!isSender) transaction.receiver else otherAddress
        )
    }

    private fun parseMultiESDTNFTTransfer(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        val token = args[3].fromHexString().split("-").first()
        var value = Value.extractHex(args[5], token)
        var otherValue = Value.None
        var transactionType = TransactionType.Unknown

        val otherAddress = Address(args[1]).erd
        val isSender = transaction.sender == referenceAddress

        when (args.getOrNull(9)?.fromHexString() ?: args.getOrNull(6)?.fromHexString()) {
            "enterFarmProxy", "enterFarm", "enterFarmAndLockRewards" -> {
                transactionType = TransactionType.EnterFarm
            }
            "addLiquidity" -> {
                val values = extractMultiESDTNFTTransferValue(transaction.data.fromBase64String())
                value = values.first()
                otherValue = values.last()
                transactionType = TransactionType.EnterLP
            }
            null -> {
                // normal transfer
                transactionType = if (isSender) TransactionType.Send else TransactionType.Receive
            }
        }

        return transaction.copy(
            value = value,
            otherValue = otherValue,
            type = transactionType,
            sender = if (isSender) transaction.sender else otherAddress,
            receiver = if (!isSender) transaction.receiver else otherAddress
        )
    }

    private fun parseOtherTransactions(referenceAddress: String, transaction: Transaction, args: List<String>): Transaction {
        var value = Value.None
        var transactionType = TransactionType.Unknown

        when (args.firstOrNull()) {
            "delegate", "stake" -> {
                value = transaction.transactionValue
                transactionType = TransactionType.Delegate
            }
            "modifyTotalDelegationCap" -> {
                value = Value.extractHex(args[1], "EGLD")
                transactionType = TransactionType.ModifyDelegationCap
            }
            "claimRewards" -> {
                val claimScResult = transaction.scResults.first { it.data.isEmpty() }
                value = Value.extract(claimScResult.value, "EGLD")
                transactionType = TransactionType.Claim
            }
            "unDelegate", "unStake" -> {
                value = Value.extractHex(args[1], "EGLD")
                transactionType = TransactionType.Undelegate
            }
            "reDelegateRewards" -> {
                val claimScResult = transaction.scResults.first { it.data.isEmpty() }
                value = Value.extract(claimScResult.value, "EGLD")
                transactionType = TransactionType.Compound
            }
            "withdraw", "unBond" -> {
                val claimScResult = transaction.scResults
                    .filter { it.receiver == referenceAddress }
                    .first { it.data.isEmpty() }
                value = Value.extract(claimScResult.value, "EGLD")
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
                }
            }
        }

        return transaction.copy(
            value = value,
            type = transactionType
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
        val first: Value
        val second: Value
        if (args[4].isNotEmpty()) {
            first = Value.extractHex(args[4], args[2].tokenFromArg())
            second = Value.extractHex(args[7], args[5].tokenFromArg())
        } else {
            first = Value.extractHex(args[5], args[3].tokenFromArg())
            second = Value.extractHex(args[8], args[6].tokenFromArg())
        }
        return listOf(first, second)
    }

    private fun String.isESDTTypeTransfer() =
        startsWith("ESDTTransfer") || startsWith("ESDTNFTTransfer") || startsWith("MultiESDTNFTTransfer")

    private const val METACHAIN = "4294967295"
}