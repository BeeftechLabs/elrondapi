package com.beeftechlabs.repository.token.address

import com.beeftechlabs.model.address.AddressDetails
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.repository.StakingRepository
import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.service.GatewayService

object AddressRepository {

    suspend fun getAddressDetails(address: String): AddressDetails {
        val account = getAccountFromGateway(address)

        return AddressDetails(
            address = address,
            nonce = account.nonce,
            balance = Value.extract(account.balance, "EGLD"),
            herotag = account.username,
            ownerAddress = account.ownerAddress,
            tokens = TokenRepository.getTokensForAddress(address),
            delegations = StakingRepository.getDelegations(address)
        )
    }

    private suspend fun getAccountFromGateway(address: String): Account =
        GatewayService.get<GetAccountResponse>("address/$address").data.account
}