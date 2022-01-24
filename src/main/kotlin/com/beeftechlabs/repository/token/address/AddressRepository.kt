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

    suspend fun getAddressDetails(
        address: String,
        withDelegations: Boolean,
        withTokens: Boolean,
        withNfts: Boolean,
        withSfts: Boolean,
        withStake: Boolean
    ): AddressDetails = coroutineScope {
        val tokens = async { if (withTokens) TokenRepository.getTokensForAddress(address) else null }
        val nfts = async { if (withNfts) TokenRepository.getNftsForAddress(address) else null }
        val sfts = async { if (withSfts) TokenRepository.getSftsForAddress(address) else null }
        val delegations = async { if (withDelegations) StakingRepository.getDelegations(address) else null }
        val staked = async { if (withStake) StakingRepository.getStaked(address) else null }
        val account = withContext(Dispatchers.IO) { getAccountFromGateway(address) }

        val totalStaked = staked.await()?.first
        val unstaked = staked.await()?.second

        AddressDetails(
            address = address,
            nonce = account.nonce,
            balance = Value.extract(account.balance, "EGLD"),
            herotag = account.username,
            ownerAddress = account.ownerAddress,
            tokens = tokens.await(),
            nfts = nfts.await(),
            sfts = sfts.await(),
            delegations = delegations.await(),
            staked = totalStaked,
            unstaked = unstaked
        )
    }

    private suspend fun getAccountFromGateway(address: String): Account =
        GatewayService.get<GetAccountResponse>("address/$address").data.account
}