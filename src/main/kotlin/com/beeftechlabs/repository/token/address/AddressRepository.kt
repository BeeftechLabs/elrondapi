package com.beeftechlabs.repository.token.address

import com.beeftechlabs.model.address.AddressDetails
import com.beeftechlabs.model.token.Value
import com.beeftechlabs.repository.StakingRepository
import com.beeftechlabs.repository.token.TokenRepository
import com.beeftechlabs.service.GatewayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object AddressRepository {

    suspend fun getAddressDetails(address: String): AddressDetails = coroutineScope {
        val tokens = async { TokenRepository.getTokensForAddress(address) }
        val delegations = async { StakingRepository.getDelegations(address) }
        val account = withContext(Dispatchers.IO) { getAccountFromGateway(address) }

        AddressDetails(
            address = address,
            nonce = account.nonce,
            balance = Value.extract(account.balance, "EGLD"),
            herotag = account.username,
            ownerAddress = account.ownerAddress,
            tokens = tokens.await(),
            delegations = delegations.await()
        )
    }

    private suspend fun getAccountFromGateway(address: String): Account =
        GatewayService.get<GetAccountResponse>("address/$address").data.account
}