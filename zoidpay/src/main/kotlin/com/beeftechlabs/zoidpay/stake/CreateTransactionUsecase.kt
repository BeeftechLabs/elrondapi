package com.beeftechlabs.zoidpay.stake

import com.beeftechlabs.config
import com.beeftechlabs.model.address.Address
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.model.transaction.NewTransaction
import com.beeftechlabs.util.toHexString
import io.ktor.util.*

object CreateTransactionUsecase {

    private val zoidPayConfig by lazy { config.zoidpay!! }

    fun stake(address: String, pool: String, value: Value, numMonths: Int, nonce: Long): NewTransaction {
        val tokenId = value.token.toHexString()
        val hexValue = value.hexValue()
        val hexPool = Address(pool).hex
        val hexMonths = numMonths.toString(16)

        return NewTransaction(
            chainId = "1",
            data = "ESDTTransfer@$tokenId@$hexValue$STAKE_FUNC$hexPool$hexMonths".encodeBase64(),
            gasLimit = 10000000,
            gasPrice = 1000000000,
            nonce = nonce,
            receiver = zoidPayConfig.stakingSC,
            sender = address,
            signature = "",
            value = "0",
            version = 2
        )
    }

    fun claim(address: String, nonce: Long): NewTransaction {
        return NewTransaction(
            chainId = "1",
            data = "claimRewards".encodeBase64(),
            gasLimit = 10000000,
            gasPrice = 1000000000,
            nonce = nonce,
            receiver = zoidPayConfig.stakingSC,
            sender = address,
            signature = "",
            value = "0",
            version = 2
        )
    }

    private const val STAKE_FUNC = "7374616b65"
}