package com.beeftechlabs.zoidpay.stake

import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.NewTransaction
import com.beeftechlabs.util.ensureHexLength
import com.beeftechlabs.util.toHexString

object CreateTransactionUsecase {

    private val zoidPayConfig by lazy { config.zoidpay!! }

    private val defaultPoolHex by lazy { Address(zoidPayConfig.pool).hex }

    fun stake(address: String, pool: String, value: Value, numMonths: Int, nonce: Long): NewTransaction {
        val tokenIdHex = value.token.toHexString()
        val hexValue = value.hexValue()
        val hexPool = Address(pool).hex
        val hexMonths = numMonths.toString(16).ensureHexLength()

        return NewTransaction(
            chainId = "1",
            data = "ESDTTransfer@$tokenIdHex@$hexValue@$STAKE_FUNC@$hexPool@$hexMonths",
            gasLimit = 20000000,
            gasPrice = 1000000000,
            nonce = nonce,
            receiver = zoidPayConfig.stakingSC,
            sender = address,
            signature = "",
            value = "0",
            version = 1
        )
    }

    fun claim(address: String, nonce: Long, pool: String?): NewTransaction {
        val poolHex = pool?.let { Address(it).hex } ?: defaultPoolHex
        return NewTransaction(
            chainId = "1",
            data = "claimRewards@$poolHex",
            gasLimit = 30000000,
            gasPrice = 1000000000,
            nonce = nonce,
            receiver = zoidPayConfig.stakingSC,
            sender = address,
            signature = "",
            value = "0",
            version = 1
        )
    }

    private const val STAKE_FUNC = "7374616b65"
}