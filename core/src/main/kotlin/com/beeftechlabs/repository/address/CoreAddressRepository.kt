package com.beeftechlabs.repository.address

import com.beeftechlabs.model.core.LongValue
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.repository.address.model.Account
import com.beeftechlabs.repository.address.model.GetAccountResponse
import com.beeftechlabs.service.GatewayService

object CoreAddressRepository {

    suspend fun getAddressBalance(address: String): Value = Value.extract(
        getAccountFromGateway(address).balance,
        "EGLD"
    ) ?: Value.zeroEgld()

    suspend fun getAddressNonce(address: String): LongValue =
        LongValue(getAccountFromGateway(address).nonce)

    suspend fun getAccountFromGateway(address: String): Account =
        GatewayService.get<GetAccountResponse>("address/$address").data.account
}